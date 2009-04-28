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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;

import com.ibm.jaql.json.type.Item;
import com.ibm.jaql.lang.core.Context;
import com.ibm.jaql.lang.core.JFunction;
import com.ibm.jaql.lang.core.Var;
import com.ibm.jaql.lang.core.VarMap;

/**
 * exprs:
 *    BindingExpr(param)
 *    ...
 *    Expr body
 */
public final class DefineFunctionExpr extends Expr
{
  protected static Expr[] makeArgs(Var[] params, Expr body)
  {
    Expr[] args = new Expr[params.length+1];
    for(int i = 0 ; i < params.length ; i++)
    {
      args[i] = new BindingExpr(BindingExpr.Type.EQ, params[i], null, Expr.NO_EXPRS);
    }
    args[params.length] = body;
    return args;
  }

  protected static Expr[] makeArgs(ArrayList<Var> params, Expr body)
  {
    int p = params.size();
    Expr[] args = new Expr[p+1];
    for(int i = 0 ; i < p ; i++)
    {
      args[i] = new BindingExpr(BindingExpr.Type.EQ, params.get(i), null, Expr.NO_EXPRS);
    }
    args[p] = body;
    return args;
  }

  /**
   * 
   * @param exprs
   */
  public DefineFunctionExpr(Expr[] exprs)
  {
    super(exprs);
  }

  /**
   * @param params
   * @param body
   */
  public DefineFunctionExpr(Var[] params, Expr body)
  {
    super(makeArgs(params, body));
  }

  /**
   * @param fnVar
   * @param params
   * @param body
   */
  public DefineFunctionExpr(ArrayList<Var> params, Expr body)
  {
    super(makeArgs(params, body));
  }

  /**
   * @return
   */
  public int numParams()
  {
    return exprs.length - 1;
  }
  
  /**
   * @return
   */
  public BindingExpr param(int i)
  {
    return (BindingExpr)exprs[i];
  }

  /**
   * @return
   */
  public Expr body()
  {
    return exprs[exprs.length-1];
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.jaql.lang.expr.core.Expr#isConst()
   */
  @Override
  public boolean isConst()
  {
    // TODO: make more efficient
    HashSet<Var> capturedVars = new HashSet<Var>();
    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    PrintStream exprText = new PrintStream(outStream);
    try
    {
      this.decompile(exprText, capturedVars);
      boolean noCaptures = capturedVars.isEmpty();
      return noCaptures;
    }
    catch (Exception ex)
    {
      return false;
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.jaql.lang.expr.core.Expr#decompile(java.io.PrintStream,
   *      java.util.HashSet)
   */
  @Override
  public void decompile(PrintStream exprText, HashSet<Var> capturedVars)
      throws Exception
  {
    exprText.print("fn");
    exprText.print("(");
    String sep = "";
    int n = numParams();
    for(int i = 0 ; i < n ; i++)
    {
      BindingExpr b = param(i);
      exprText.print(sep);
      exprText.print(b.var.name);
      sep = ", ";
    }
    exprText.print(") ( ");
    body().decompile(exprText, capturedVars);
    exprText.println(" )");

    for(int i = 0 ; i < n ; i++)
    {
      capturedVars.remove(param(i).var);
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.jaql.lang.expr.core.Expr#eval(com.ibm.jaql.lang.core.Context)
   */
  @Override
  public Item eval(Context context) throws Exception
  {
    this.annotate(); // TODO: move to init call
    DefineFunctionExpr f = this;
    HashSet<Var> capturedVars = this.getCapturedVars(); // TODO: optimize
    int n = capturedVars.size();
    if( n > 0 )
    {
      // If we have captured variables, we need to evaluate and save their value now.
      // We do this by making new local variables in the function that store the captured values.
      // To add new local variables, we have to define a new function.
      // TODO: is it safe to share f when we don't have captures?
      VarMap varMap = new VarMap(null);
      for(Var oldVar: capturedVars)
      {
        Var newVar = new Var(oldVar.name());
        varMap.put(oldVar, newVar);
      }
      f = (DefineFunctionExpr)this.clone(varMap);
      Expr[] es = new Expr[n + 1];
      int i = 0;
      for( Var v: capturedVars )
      {
        Item val = new Item();
        val.setCopy(v.getValue());
        es[i++] = new BindingExpr(BindingExpr.Type.EQ, varMap.get(v), null, new ConstExpr(val));
      }
      es[n] = f.body().injectAbove();
      new DoExpr(es);
    }
    JFunction fn = new JFunction(f, n > 0);
    return new Item(fn);
  }

  public void annotate()
  {
    int p = numParams();
    if( p == 0 )
    {
      return;
    }
    ArrayList<Expr> uses = new ArrayList<Expr>();
    Expr body = body();
    for(int i = 0 ; i < p ; i++)
    {
      uses.clear();
      BindingExpr b = param(i);
      b.var.usage = Var.Usage.EVAL;
      body.getVarUses(b.var, uses);
      int n = uses.size();
      if( n == 0 )
      {
        b.var.usage = Var.Usage.UNUSED;
      }
      else if( n == 1 )
      {
        Expr e = uses.get(0);
        while( e != body )
        {
          if( e.isEvaluatedOnceByParent().maybeNot() )
          {
            break;
          }
          e = e.parent();
        }
        if( e == body )
        {
          b.var.usage = Var.Usage.STREAM;
        }
      }
    }
  }
}
