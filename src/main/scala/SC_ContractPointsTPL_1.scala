

import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.app.conf.SystemProfile
import rep.protos.peer.ChaincodeId
import rep.utils.IdTool

import java.text.SimpleDateFormat
import rep.sc.scalax.{ContractContext, ContractException, IContract}
import rep.protos.peer.ActionResult


/**
 * 积分管理合约
 */
//花溪区明珠大道192号区人民政府一楼区卫生健康局C101人事办公室。
final case class SC_ContractPointsTPL_1TransferForLegal(from:String, to:String, amount:Int, remind:String)
final case class SC_ContractPointsTPL_1TopUpForLegal(from:String, to:String, amount:Int)
final case class count_test(from: String, count: Int)
class SC_ContractPointsTPL_1 extends IContract{

  val chaincodeName = SystemProfile.getAccountChaincodeName
  val chaincodeVersion = SystemProfile.getAccountChaincodeVersion
  val signerNotExists = "账户不存在"

  implicit val formats = DefaultFormats

  def init(ctx: ContractContext){
    println(s"tid: $ctx.t.id")
  }

  // 积分充值
  def topup(ctx: ContractContext, data:SC_ContractPointsTPL_1TopUpForLegal) :ActionResult={
    if (data.from != "121000005l35120456") {
      println("非管理员不能进行充值操作")
      return new ActionResult(-1, "非管理员不能进行充值操作")
    }

    // 判断 data.to 是否存在
    val signer = ctx.api.getState(data.to)

    // 若账户不存在，抛出异常
    if(signer == null) {
      throw ContractException(signerNotExists)
    }

    println(s"set points:$data")
    ctx.api.addPoints(data.to, data.amount)
    new ActionResult(1)
  }

  def transfer(ctx: ContractContext, data:SC_ContractPointsTPL_1TransferForLegal) :ActionResult={
    println("####################################################")
    if(!data.from.equals(ctx.t.getSignature.getCertId.creditCode)) {
      println("data.from: " + data.from)
      println("getSignature: " + ctx.t.getSignature.getCertId.creditCode)
      println("只允许从本人账户转出")
      return new ActionResult(-1, "只允许从本人账户转出")
    }

    val signerKey =  data.to
    if(ctx.api.getStateEx(chaincodeName,data.to)==null)
      return new ActionResult(-2, "目标账户不存在")

    // 判断积分余额是否足够
    // 余额不足 结束交易
    if(!ctx.api.checkPoints(data.from, chaincodeName, data.amount)) {
      return new ActionResult(-3, "余额不足")
    }
    // 更新转账后的积分
    ctx.api.addPoints(data.to, data.amount)
    return new ActionResult(1)
  }

  /**
   * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
   */
  def onAction(ctx: ContractContext,action:String, sdata:String ):ActionResult={
    val json = parse(sdata)
    action match {
      case "transfer" =>
        transfer(ctx,json.extract[SC_ContractPointsTPL_1TransferForLegal])
      case "topup" =>
        topup(ctx, json.extract[SC_ContractPointsTPL_1TopUpForLegal])
    }
  }

}
