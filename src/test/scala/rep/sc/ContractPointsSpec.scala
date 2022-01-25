package rep.sc

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.{RepChainMgr, StartParameter}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.autotransaction.PeerHelper
import rep.network.cluster.MemberListener
import rep.network.module.cfrd.ModuleManagerOfCFRD
import rep.protos.peer.{Certificate, ChaincodeId, Signer}
import rep.sc.TransferSpec.{ACTION, SetMap}
import rep.sc.tpl._
//.{CertStatus,CertInfo}
import rep.sc.tpl.Transfer
import rep.storage.ImpDataAccess
import rep.utils.SerializeUtils.toJson
import rep.app.conf.SystemProfile

import scala.concurrent.duration._
import scala.collection.mutable.Map
import rep.sc.SandboxDispatcher.DoTransaction


/**
 * author zyf
 *
 * @param _system
 */
class ContractPointsSpec(_system: ActorSystem) extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll {

  case class Transfer(from: String, to: String, amount: Int, remind: String)

  case class TopUp(from: String, to: String, amount: Int)

  def this() = this({
    var sys1 = new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, true)
    sys1.init
//    val joinAddress = sys1.getClusterAddr
//    sys1.joinCluster(joinAddress)
//    sys1.enableWS()
//    sys1.start
//    ActorSystem("TransferSpec", sys1.getConf)
    sys1.getSysActor
  })

  override def afterAll: Unit = {
    shutdown(system)
  }

  object ACTION {
    val transfer = "transfer"
    val set = "set"
    val SignUpSigner = "SignUpSigner"
    val SignUpCert = "SignUpCert"
    val UpdateCertStatus = "UpdateCertStatus"
    val UpdateSigner = "UpdateSigner"
    val get = "get"
  }

  "ContractPointsTPL" should "can set assets and transfer from a to b" in {
    val sysName = "121000005l35120456.node1"
    val dbTag = "121000005l35120456.node1"

//    var nodelist : Array[String] = new Array[String] (5)
//    nodelist(0) = "121000005l35120456.node1"
//    nodelist(1) = "12110107bi45jh675g.node2"
//    nodelist(2) = "122000002n00123567.node3"
//    nodelist(3) = "921000005k36123789.node4"
//    nodelist(4) = "921000006e0012v696.node5"
//    var nodeports : Array[Int] = new Array[Int](5)
//    nodeports(0) = 22522
//    nodeports(1) = 22523
//    nodeports(2) = 22524
//    nodeports(3) = 22525
//    nodeports(4) = 22526
//
//
//
//    for(i <- 1 to 4) {
//      Thread.sleep(5000)
//      RepChainMgr.Startup4Multi(new StartParameter(nodelist(i),Some(nodeports(i)),None))
//    }

//    var ac = system.actorOf(MemberListener.props("memberlis"), "memberlis")

    //建立PeerManager实例是为了调用transactionCreator(需要用到密钥签名)，无他
    val pm = system.actorOf(ModuleManagerOfCFRD.props("modulemanagercfrd", sysName, false, false, false), "modulemanagercfrd")

    // 部署资产管理
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractPointsTPL.scala")
    val l1 = try s1.mkString finally s1.close()
    // 部署账户管理合约
    val s2 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractCert.scala")
    val l2 = try s2.mkString finally s2.close()
    val sm: SetMap = Map("121000005l35120456" -> 20, "12110107bi45jh675g" -> 50, "122000002n00123567" -> 50)
    val sms = toJson(sm)

    //val aa = new ContractCert
    val signer = Signer("node2", "12110107bi45jh675g", "13856789234", Seq("node2"))
    val cert = scala.io.Source.fromFile("jks/certs/12110107bi45jh675g.node2.cer")
    val certStr = try cert.mkString finally cert.close()
    val certinfo = CertInfo("12110107bi45jh675g", "node2", Certificate(certStr, "SHA256withECDSA", true, None, None))
    //准备探针以验证调用返回结果
    val probe = TestProbe()
    val db = ImpDataAccess.GetDataAccess(sysName)
    val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")

    val cid2 = ChaincodeId(SystemProfile.getAccountChaincodeName, 1)
    val cid1 = ChaincodeId("ContractPointsTPL", 1)

    //生成deploy交易
    val t1 = PeerHelper.createTransaction4Deploy(sysName, cid1, l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send1 = DoTransaction(t1, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send1)
    val msg_recv1 = probe.expectMsgType[rep.protos.peer.TransactionResult](1000.seconds)
    //    msg_recv1.err should be(None)
//    ac ! t1

    val t2 = PeerHelper.createTransaction4Deploy(sysName, cid2, l2, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send2 = DoTransaction(t2, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send2)
    val msg_recv2 = probe.expectMsgType[rep.protos.peer.TransactionResult](1000.seconds)
    //    msg_recv2.err.isEmpty should be(true)

    // 生成invoke交易
    // 注册账户
    val t3 = PeerHelper.createTransaction4Invoke(sysName, cid2, ACTION.SignUpSigner, Seq(toJson(signer)))
    val msg_send3 = DoTransaction(t3, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send3)
    val msg_recv3 = probe.expectMsgType[rep.protos.peer.TransactionResult](1000.seconds)
    //    msg_recv3.err.isEmpty should be(true)

    // 注册证书
    val t4 = PeerHelper.createTransaction4Invoke(sysName, cid2, ACTION.SignUpCert, Seq(toJson(certinfo)))
    val msg_send4 = DoTransaction(t4, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send4)
    val msg_recv4 = probe.expectMsgType[rep.protos.peer.TransactionResult](1000.seconds)
    //    msg_recv4.err.isEmpty should be(true)

    // new
    // 生成查询余额的交易
//    val t6 = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.get, Seq(toJson("121000005l35120456")))
//    val msg_send6 = DoTransaction(t6, "dbnumber", TypeOfSender.FromAPI)
//    probe.send(sandbox, msg_send6)
//    val msg_recv6 = probe.expectMsgType[rep.protos.peer.TransactionResult](1000.seconds)

    //生成invoke交易
    var top = TopUp("121000005l35120456", "12110107bi45jh675g", 50)
//    val t5 = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.set, Seq(sms))
    val t5 = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.set, Seq(toJson(top)))
    val msg_send5 = DoTransaction(t5, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send5)
    val msg_recv5 = probe.expectMsgType[rep.protos.peer.TransactionResult](1000.seconds)
    //    msg_recv5.r.code should be(1)

    // 非管理员充值
    var top2 = TopUp("12110107bi45jh675g", "12110107bi45jh675g", 1000)
    val test_top = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.set, Seq(toJson(top2)))
    val msg = DoTransaction(test_top, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg)
    val recv = probe.expectMsgType[rep.protos.peer.TransactionResult](1000.seconds)
    println(recv.result)

    //获得提醒文本
    for(i <- 0 to 6) {
      val rstr =s"""确定签名从本人所持有的账户【121000005l35120456】
          向账户【12110107bi45jh675g】
          转账【3】元"""
      var p = Transfer("121000005l35120456", "12110107bi45jh675g", 3, rstr)
      var t = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.transfer, Seq(toJson(p)))
      var msg_send = DoTransaction(t, "dbnumber", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      var msg_recv = probe.expectMsgType[rep.protos.peer.TransactionResult](1000.seconds)
      //    msg_recv.r.code should be(2)
      println(msg_recv.result)

      // new
      // 获取余额
      val t6 = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.get, Seq(toJson("121000005l35120456")))
      val msg_send6 = DoTransaction(t6, "dbnumber", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send6)
      val msg_recv7 = probe.expectMsgType[rep.protos.peer.TransactionResult](1000.seconds)
    }
    //对提醒文本签名
    //    val remind = msg_recv.r.reason
    //    println(remind)
    //    p = Transfer("121000005l35120456", "12110107bi45jh675g", 5, remind)
    //    t = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.transfer, Seq(toJson(p)))
    //    msg_send = DoTransaction(t, "dbnumber", TypeOfSender.FromAPI)
    //    probe.send(sandbox, msg_send)
    //    msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    //    msg_recv.r.code should be(1)
    wait(300000)
  }
}

