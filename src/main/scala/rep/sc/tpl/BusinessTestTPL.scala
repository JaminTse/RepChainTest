package rep.sc.tpl

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

final case class SC_ContractPointsTPL_1TransferForLegal2(from:String, to:String, amount:Int, remind:String)
final case class SC_ContractPointsTPL_1TopUpForLegal2(from:String, to:String, amount:Int)
class BusinessTestTPL extends IContract{


  // 需要跨合约读账户
  val chaincodeName = SystemProfile.getAccountChaincodeName
  val chaincodeVersion = SystemProfile.getAccountChaincodeVersion
  // new
  var calledCount: Int = 0

  implicit val formats = DefaultFormats

  def init(ctx: ContractContext){
    println(s"tid: $ctx.t.id")
  }

  def set(ctx: ContractContext, data:SC_ContractPointsTPL_1TopUpForLegal2) : ActionResult={
    var actorSys = GlobalUtils.systems(0)
    val td = actorSys.actorOf(TransactionDispatcher.props("td22"),  "td22")
    val ph = actorSys.actorOf(PeerHelper.props("ph22"), "ph22")

    val cid1 = ChaincodeId("ContractPointsTPL", 1)
    val t = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.get, Seq(toJson("121000005l35120456")))
    val msg_send = DoTransaction(t, "dbnumber", TypeOfSender.FromAPI)
    td ! msg_send
    val tt = TestInvoke2(t)
    ph! tt

    println("Business Contract Test, function set called")
    new ActionResult(1)
  }

  def get(ctx: ContractContext, key: String): ActionResult = {
    println("Business Contract Test, function get called")
    new ActionResult(1)
  }

  def onAction(ctx: ContractContext,action:String, sdata:String ):ActionResult={
    val json = parse(sdata)
    action match {
      case "set" =>
        set(ctx, json.extract[SC_ContractPointsTPL_1TopUpForLegal2])
      case "get" =>
        get(ctx, json.extract[String])
    }
  }

}
