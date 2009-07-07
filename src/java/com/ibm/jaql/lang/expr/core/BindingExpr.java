/*
 * Copyright (C) IBM Corp. 2008.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.ibm.jaql.lang.expr.core;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Map;

import com.ibm.jaql.json.schema.Schema;
import com.ibm.jaql.json.type.JsonValue;
import com.ibm.jaql.json.util.JsonIterator;
import com.ibm.jaql.lang.core.Context;
import com.ibm.jaql.lang.core.Var;
import com.ibm.jaql.lang.core.VarMap;
import com.ibm.jaql.util.Bool3;

/**
 * A BindingExpr is not really an Expr at all. It is used to associate a
 * variable with its definition (or one of its definitions in the case of
 * GROUP_BY). It should never be evaluated or decompiled because the expressions
 * that use bindings know how to walk over these guys.
 */
public class BindingExpr extends Expr
{
  public static enum Type
  {
    EQ(), IN(), INREC(), INPAIR(), INAGG(), AGGFN(),
  }

  public Type type;

  //           exprs[0]        exprs[1]
  //    EQ     defining expr   n/a
  //    IN     array expr      the on-clause of "join"
  //    INREC  record expr     n/a

  // var is always required
  //    EQ     the variable being defined by "let", or by-clause of "group"
  //    IN     the iteration variable defined by "for", "join", or in-clause of "group"
  //    INREC  the name variable
  public Var     var;             // FIXME: make protected

  // var2
  //    EQ     null
  //    IN     optional "at" index for "for"
  //           required "into" variable for "group"
  //    INREC  required value variable
  public Var     var2;            // FIXME: make protected

  /**
   * For a join expression, true means this input is preserved in the output (no records dropped).
   */
  public boolean preserve = false; // FIXME: make protected

  /**
   * @param type
   * @param var
   * @param var2
   * @param optional
   * @param exprs
   */
  public BindingExpr(Type type, Var var, Var var2, boolean preserved, Expr[] exprs)
  {
    super(exprs);
    this.type = type;
    this.var = var;
    this.var2 = var2;
    this.preserve = preserved;
  }

  /**
   * 
   * @param type
   * @param var
   * @param var2
   * @param preserved
   * @param expr
   */
  public BindingExpr(Type type, Var var, Var var2, boolean preserved, Expr expr)
  {
    super(expr);
    this.type = type;
    this.var = var;
    this.var2 = var2;
    this.preserve = preserved;
  }

  /**
   * @param type
   * @param var
   * @param var2
   * @param exprs
   */
  public BindingExpr(Type type, Var var, Var var2, Expr[] exprs)
  {
    this(type, var, var2, false, exprs);
  }

  /**
   * @param type
   * @param var
   * @param var2
   * @param expr
   */
  public BindingExpr(Type type, Var var, Var var2, Expr expr)
  {
    this(type, var, var2, false, new Expr[]{expr});
  }

  /**
   * @param type
   * @param var
   * @param var2
   * @param expr0
   * @param expr1
   */
  public BindingExpr(Type type, Var var, Var var2, Expr expr0, Expr expr1)
  {
    this(type, var, var2, false, new Expr[]{expr0, expr1});
  }

  public Bool3 getProperty(ExprProperty prop, boolean deep)
  {
    Map<ExprProperty, Boolean> props = getProperties();
    if (deep)
    {
      return getProperty(props, prop, new Expr[] { exprs[0] });
    }
    else
    {
      return getProperty(props, prop, null);
    }    
  }
  
  
  /**
   * 
   */
  @Override
  public Bool3 evaluatesChildOnce(int i)
  {
    return Bool3.TRUE;
  }


  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.jaql.lang.expr.core.Expr#decompile(java.io.PrintStream,
   *      java.util.HashSet)
   */
  public void decompile(PrintStream exprText, HashSet<Var> capturedVars)
      throws Exception
  {
    exprText.print(var.name);
    exprText.print(" = ");
    if( exprs.length == 0 )
    {
      exprText.print("??");
    }
    else
    {
      exprs[0].decompile(exprText, capturedVars);
    }
    // throw new RuntimeException("BindingExpr should never be decompiled");
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.jaql.lang.expr.core.Expr#eval(com.ibm.jaql.lang.core.Context)
   */
  @Override
  public JsonValue eval(Context context) throws Exception
  {
    //throw new RuntimeException("BindingExpr should never be evaluated");
    var.setEval(exprs[0], context); // TODO: set var.usage
    return null;
  }

  /**
   * Returns iter that returns the input plus a side-effect of setting the variable
   * to each element. 
   */
  @Override
  public JsonIterator iter(final Context context) throws Exception
  {
    //throw new RuntimeException("BindingExpr should never be evaluated");
    var.undefine();
    final JsonIterator iter = exprs[0].iter(context);
    return new JsonIterator()
    {
      @Override
      public boolean moveNext() throws Exception
      {
        if (iter.moveNext()) {
          currentValue = iter.current();
          var.setValue(currentValue);
          return true;
        } 
        var.undefine();
        return false;
      }
    };
  }
  
  public Schema getSchema()
  {
    return exprs[0].getSchema();
  }
  
  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.jaql.lang.expr.core.Expr#clone(com.ibm.jaql.lang.core.VarMap)
   */
  public BindingExpr clone(VarMap varMap)
  {
    return new BindingExpr(type, varMap.remap(var), varMap.remap(var2),
        preserve, cloneChildren(varMap));
  }

  /**
   * @return
   */
  public final Expr eqExpr()
  {
    return exprs[0];
  }

  /**
   * @return
   */
  public final Expr inExpr()
  {
    return exprs[0];
  }

//  /**
//   * @return
//   */
//  public final Expr onExpr()
//  {
//    return exprs[1];
//  }
//
  /**
   * @param i
   * @return
   */
  public final Expr byExpr(int i)
  {
    return exprs[i];
  }

}
