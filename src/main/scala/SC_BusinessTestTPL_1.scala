

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

final case class SC_BusinessTestTPL_1SC_ContractPointsTPL_1TransferForLegal2(from:String, to:String, amount:Int, remind:String)
final case class SC_BusinessTestTPL_1SC_ContractPointsTPL_1TopUpForLegal2(from:String, to:String, amount:Int)
class SC_BusinessTestTPL_1 extends IContract{

  var actorSys = GlobalUtils.systems(0)
  val td = actorSys.actorOf(TransactionDispatcher.props("td2"),  "td2")
  val ph = actorSys.actorOf(PeerHelper.props("ph2"), "ph2")
  val cid1 = ChaincodeId("ContractPointsTPL", 1)
  var calledCount: Int = 0

  implicit val formats = DefaultFormats

  def init(ctx: ContractContext){
    println(s"tid: $ctx.t.id")
  }

  def printing(ctx: ContractContext, data:SC_BusinessTestTPL_1SC_ContractPointsTPL_1TopUpForLegal2) : ActionResult={
    calledCount = (ctx.api.getCounts(data.from)).asInstanceOf[Int]
    calledCount += 1
    ctx.api.setCounts(data.from, calledCount)

//    val t = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.get, Seq(toJson("121000005l35120456")))
//    val msg_send = DoTransaction(t, "dbnumber", TypeOfSender.FromAPI)
//    td ! msg_send
//    val tt = TestInvoke2(t)
//    ph ! tt
//
//    var top2 = TopUp("12110107bi45jh675g", "12110107bi45jh675g", 1000)
//    val t6 = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.set, Seq(toJson(top2)))
//    val msg_send6 = DoTransaction(t6, "dbnumber", TypeOfSender.FromAPI)
//    td ! msg_send6
//    //    tp ! test_top
//    val tt6 = TestInvoke2(t6)
//    ph ! tt6

    val cost_detail = count_test(data.from, calledCount)
    val cost = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.CostAndSet2, Seq(toJson(cost_detail)))
    val cost_msg = DoTransaction(cost, "dbnumber", TypeOfSender.FromAPI)
    td ! cost_msg
    val cost_t = TestInvoke2(cost)
    ph ! cost

    println("Business Contract Test, function printing called")
    new ActionResult(1)
  }

  def onAction(ctx: ContractContext,action:String, sdata:String ):ActionResult={
    val json = parse(sdata)
    action match {
      case "printing" =>
        printing(ctx, json.extract[SC_BusinessTestTPL_1SC_ContractPointsTPL_1TopUpForLegal2])
    }
  }

}
