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
package com.ibm.jaql.lang.expr.hadoop;

import org.apache.hadoop.mapred.JobConf;

import com.ibm.jaql.io.hadoop.Globals;
import com.ibm.jaql.json.type.Item;
import com.ibm.jaql.json.type.JString;
import com.ibm.jaql.lang.core.Context;
import com.ibm.jaql.lang.expr.core.Expr;
import com.ibm.jaql.lang.expr.core.JaqlFn;

/**
 * 
 */
@JaqlFn(fnName = "readConf", minArgs = 1, maxArgs = 2)
public class ReadConfExpr extends Expr
{
  /**
   * @param exprs
   */
  public ReadConfExpr(Expr[] exprs)
  {
    super(exprs);
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.jaql.lang.expr.core.Expr#eval(com.ibm.jaql.lang.core.Context)
   */
  @Override
  public Item eval(Context context) throws Exception
  {
    JobConf conf = Globals.getJobConf();
    Item dflt = Item.NIL;
    if (conf == null)
    {
      if (exprs.length == 2)
      {
        dflt = exprs[1].eval(context);
      }
      return dflt;
    }

    JString name = (JString) (exprs[0].eval(context).get());
    String val = conf.get(name.toString());
    if (val == null)
    {
      if (exprs.length == 2)
      {
        dflt = exprs[1].eval(context);
      }
      return dflt;
    }

    return new Item(new JString(val));
  }
}
