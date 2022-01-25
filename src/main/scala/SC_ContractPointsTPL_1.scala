

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

final case class SC_ContractPointsTPL_1TransferForLegal2(from:String, to:String, amount:Int, remind:String)
final case class SC_ContractPointsTPL_1TopUpForLegal2(from:String, to:String, amount:Int)
class SC_ContractPointsTPL_1 extends IContract{


  // 需要跨合约读账户
  val chaincodeName = SystemProfile.getAccountChaincodeName
  val chaincodeVersion = SystemProfile.getAccountChaincodeVersion
  // new
  var calledCount: Int = 0
  //val prefix = IdTool.getCid(ChaincodeId(chaincodeName, chaincodeVersion))

  implicit val formats = DefaultFormats

  def init(ctx: ContractContext){
    println(s"tid: $ctx.t.id")
  }

  def set(ctx: ContractContext, data:SC_ContractPointsTPL_1TopUpForLegal2) :ActionResult={
    if (data.from != "121000005l35120456") {
      return new ActionResult(-1, "非管理员不能进行充值操作")
    }
    calledCount += 1
    println("Contract called, current number count: " + calledCount)
    println(s"set points:$data")
    ctx.api.setPoints(data.to, data.amount)
    new ActionResult(1)
  }

//  def set(ctx: ContractContext, data:Map[String,Int]) :ActionResult={
//    calledCount += 1
//    println("Contract called, current number count: " + calledCount)
//    println(s"set points:$data")
//    for((k,v)<-data){
//      ctx.api.setPoints(k, v)
//    }
//    new ActionResult(1)
//  }
  def get(ctx: ContractContext, key: String): ActionResult = {
    calledCount += 1
  println("####################################################")
    println("Contract called, current number count: " + calledCount)
    costAndSet(ctx, key)
    println("Key: " + key + " has " + ctx.api.getPoints(key) + " points")
    new ActionResult(1)
  }

  def transfer(ctx: ContractContext, data:SC_ContractPointsTPL_1TransferForLegal2) :ActionResult={
    calledCount += 1
    println("####################################################")
    println("Contract called, current number count: " + calledCount)
    if( !costAndSet(ctx, data.from) ) {
      calledCount -= 1
      println("####################################################")
      println("number count roll back to " + calledCount)
      return new ActionResult(-3, "余额不足以调用合约")
    }
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
    println(rstr)
    //预执行获得结果提醒
    if(data.remind==null){
      new ActionResult(2,rstr)
    }else{
      if(!data.remind.equals(rstr))
        new ActionResult(-4,"提醒内容不符")
      else{
        var dto = ctx.api.getPoints(data.to).asInstanceOf[Int]
        ctx.api.setPoints(data.from,dfrom - data.amount)
        ctx.api.setPoints(data.to,dto + data.amount)
        new ActionResult(1)
      }
    }
  }

  def costAndSet(ctx: ContractContext, key: String): Boolean = {
    val base_cost = 2
    val real_cost: Int = 2 + (calledCount-1)
    val current_point: Int = (ctx.api.getPoints(key)).asInstanceOf[Int]
    println("real_cost: " + real_cost + "  current_point: " + current_point)
    if (current_point < real_cost) {
      return false
    }
    ctx.api.setPoints(key, current_point - real_cost)
    return true
  }

  /**
   * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
   */
  def onAction(ctx: ContractContext,action:String, sdata:String ):ActionResult={
    val json = parse(sdata)
    action match {
      case "transfer" =>
        transfer(ctx,json.extract[SC_ContractPointsTPL_1TransferForLegal2])
      case "set" =>
//        set(ctx, json.extract[Map[String,Int]])
        set(ctx, json.extract[SC_ContractPointsTPL_1TopUpForLegal2])
      case "get" =>
        get(ctx, json.extract[String])
    }
  }

}
