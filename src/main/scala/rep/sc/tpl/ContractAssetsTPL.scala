
/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rep.sc.tpl

import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.sc.contract._
import rep.sc.contract.ContractContext
import rep.sc.contract.IContract
import rep.app.conf.SystemProfile
import rep.protos.peer.ChaincodeId
import rep.utils.IdTool
/**
 * 资产管理合约
 */

case class Transfer(from:String, to:String, amount:Int)

class ContractAssetsTPL extends IContract{

  // 需要跨合约读账户
  val cName = "ContractCert"
  val version = 1
  val prefix = IdTool.getCid(ChaincodeId(cName,version))

  implicit val formats = DefaultFormats
  
    def init(ctx: ContractContext){      
      println(s"tid: $ctx.t.txid")
    }
    
    def set(ctx: ContractContext, data:Map[String,Int]) :ActionResult={
      println(s"set data:$data")
      for((k,v)<-data){
        ctx.api.setVal(k, v)
      }
      new ActionResult(1,None)
    }
    
    def transfer(ctx: ContractContext, data:Transfer) :ActionResult={
      if(!data.from.equals(ctx.t.getSignature.getCertId.creditCode))
        return new ActionResult(-1, Some("只允许从本人账户转出"))      
      val signerKey = prefix + "_" + data.to
      // 跨合约读账户，该处并未反序列化
      if(ctx.api.getStateEx(cName,signerKey)==null)
        return new ActionResult(-2, Some("目标账户不存在"))
      val sfrom =  ctx.api.getVal(data.from)
      var dfrom =sfrom.asInstanceOf[Int]
      if(dfrom < data.amount)
        new ActionResult(-3, Some("余额不足"))
      var dto = ctx.api.getVal(data.to).toString.toInt
      ctx.api.setVal(data.from,dfrom - data.amount)
      ctx.api.setVal(data.to,dto + data.amount)
       new ActionResult(1,None)
    }
    /**
     * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
     */
    def onAction(ctx: ContractContext,action:String, sdata:String ):ActionResult={
      val json = parse(sdata)      
      action match {
        case "transfer" => 
          transfer(ctx,json.extract[Transfer])
        case "set" => 
          set(ctx, json.extract[Map[String,Int]])
      }
    }
    
}
