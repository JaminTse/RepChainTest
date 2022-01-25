package rep.sc.tpl

import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.app.conf.SystemProfile
import rep.protos.peer.ChaincodeId
import rep.utils.IdTool
import java.text.SimpleDateFormat
import rep.sc.scalax.IContract
import rep.protos.peer.ActionResult
import rep.sc.scalax.ContractContext


/**
 * 积分管理合约
 */

final case class TransferForLegal2(from:String, to:String, amount:Int, remind:String)
class ContractPointsTPL extends IContract{


  // 需要跨合约读账户
  val chaincodeName = SystemProfile.getAccountChaincodeName
  val chaincodeVersion = SystemProfile.getAccountChaincodeVersion
  //val prefix = IdTool.getCid(ChaincodeId(chaincodeName, chaincodeVersion))

  implicit val formats = DefaultFormats

  def init(ctx: ContractContext){
    println(s"tid: $ctx.t.id")
  }

  def set(ctx: ContractContext, data:Map[String,Int]) :ActionResult={
    println(s"set points:$data")
    for((k,v)<-data){
      ctx.api.setPoints(k, v)
    }
    new ActionResult(1)
  }

  def transfer(ctx: ContractContext, data:TransferForLegal) :ActionResult={
    if(!data.from.equals(ctx.t.getSignature.getCertId.creditCode))
      return new ActionResult(-1, "只允许从本人账户转出")
    val signerKey =  data.to
    // 跨合约读账户，该处并未反序列化
    if(ctx.api.getStateEx(chaincodeName,data.to)==null)
      return new ActionResult(-2, "目标账户不存在")

    val sfrom:Any =  ctx.api.getPoints(data.from)
    var dfrom =sfrom.asInstanceOf[Int]
    if(dfrom < data.amount)
      return new ActionResult(-3, "余额不足")

    val rstr = s"""确定签名从本人所持有的账户【${data.from}】
          向账户【${data.to}】
          转账【${data.amount}】元"""
    //预执行获得结果提醒
    if(data.remind==null){
      new ActionResult(2,rstr)
    }else{
      if(!data.remind.equals(rstr))
        new ActionResult(-4,"提醒内容不符")
      else{
        var dto = ctx.api.getVal(data.to).toString.toInt
        ctx.api.setPoints(data.from,dfrom - data.amount)
        ctx.api.setPoints(data.to,dto + data.amount)
        new ActionResult(1)
      }
    }
  }
  /**
   * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
   */
  def onAction(ctx: ContractContext,action:String, sdata:String ):ActionResult={
    val json = parse(sdata)
    action match {
      case "transfer" =>
        transfer(ctx,json.extract[TransferForLegal])
      case "set" =>
        set(ctx, json.extract[Map[String,Int]])
    }
  }

}
