

import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.app.TestMain.sysName
import rep.app.{ACTION, TopUp}
import rep.app.conf.SystemProfile
import rep.network.autotransaction.PeerHelper
import rep.network.autotransaction.PeerHelper.TestInvoke2
import rep.protos.peer.ChaincodeId
import rep.utils.{GlobalUtils, IdTool}

import java.text.SimpleDateFormat
import rep.sc.scalax.IContract
import rep.protos.peer.ActionResult
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.{TransactionDispatcher, TypeOfSender}
import rep.sc.scalax.ContractContext
import rep.utils.SerializeUtils.toJson

import scala.collection.mutable.Seq


/**
 * 业务合约
 */

final case class SC_BusinessTestTPL_1TransferForLegal(from:String, to:String, amount:Int, remind:String)
final case class SC_BusinessTestTPL_1TopUpForLegal(from:String, to:String, amount:Int)
class SC_BusinessTestTPL_1 extends IContract{

  val cid = ChaincodeId("BusinessTestTPL", 1)
  val chaincodeName = cid.chaincodeName
  val chaincodeVersion = cid.version

  implicit val formats = DefaultFormats

  def init(ctx: ContractContext){
    println(s"tid: $ctx.t.id")
  }

  def printing(ctx: ContractContext, data:SC_BusinessTestTPL_1TopUpForLegal) : ActionResult={
    // 判断积分余额，假设调用该合约需要cost=2
    val cost = 2
    if (!ctx.api.checkPoints(data.from, chaincodeName, cost)) {
      println("#### 合约调用失败，积分余额不足 ####")
      return new ActionResult(-4, "当前积分不足")
    }

    println("Business Contract Test, function printing called successfully")
    return new ActionResult(1)
  }

  def buy(ctx: ContractContext, data:SC_BusinessTestTPL_1TopUpForLegal) : ActionResult={
    // 判断积分余额，假设调用该合约需要cost=5
    val cost = 5
    if (!ctx.api.checkPoints(data.from, chaincodeName, cost)) {
      println("#### 合约调用失败，积分余额不足 ####")
      return new ActionResult(-4, "当前积分不足")
    }

    println("Business Contract Test, function buy called successfully")
    return new ActionResult(1)
  }

  def onAction(ctx: ContractContext,action:String, sdata:String ):ActionResult={
    val json = parse(sdata)
    action match {
      case "printing" =>
        printing(ctx, json.extract[SC_BusinessTestTPL_1TopUpForLegal])
      case "buy" =>
        buy(ctx, json.extract[SC_BusinessTestTPL_1TopUpForLegal])
    }
  }
}
