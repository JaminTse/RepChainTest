/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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

package rep.app


import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster
import akka.actor.Address

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import akka.remote.transport.Transport._
import com.typesafe.config.{Config, ConfigFactory}
import rep.api.rest.RestActor
import rep.app.conf.SystemProfile
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.log.RepLogger
import rep.network._
import rep.network.autotransaction.PeerHelper
import rep.network.autotransaction.PeerHelper.{TestInvoke, TestInvoke2, createTransactionByInput}
import rep.network.cache.{ITransactionPool, TransactionChecker, TransactionOfCollectioner}
import rep.network.cache.cfrd.TransactionPoolOfCFRD
import rep.network.cache.raft.TransactionPoolOfRAFT
import rep.network.cluster.MemberListener
import rep.network.consensus.cfrd.MsgOfCFRD.{CreateBlock, VoteOfBlocker}
import rep.network.consensus.cfrd.block.BlockerOfCFRD
import rep.network.consensus.cfrd.vote.VoterOfCFRD
import rep.network.module.IModuleManager
import rep.network.module.cfrd.ModuleManagerOfCFRD
import rep.network.module.raft.ModuleManagerOfRAFT
import rep.protos.peer.{Certificate, ChaincodeId, Signer, Transaction}
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.{SandboxDispatcher, TransactionDispatcher, TypeOfSender}
import rep.sc.tpl.CertInfo
import rep.storage.ImpDataAccess
import rep.ui.web.EventServer
import rep.utils.GlobalUtils
import rep.utils.SerializeUtils.toJson

import java.io.File
import scala.collection.mutable._

case class Transfer(from: String, to: String, amount: Int, remind: String)

case class TopUp(from: String, to: String, amount: Int)

object ACTION {
  val transfer = "transfer"
  val set = "set"
  val SignUpSigner = "SignUpSigner"
  val SignUpCert = "SignUpCert"
  val UpdateCertStatus = "UpdateCertStatus"
  val UpdateSigner = "UpdateSigner"
  val get = "get"
}

object TestMain {
  type SetMap = scala.collection.mutable.Map[String, Int]
  val systemName = "repChain_"
  val name_PeerManager = "pm_"
  val name_MemberListener = "ml_"
  val system0 = ActorSystem(systemName)
  val joinAddress = Cluster(system0).selfAddress
  var m: Map[Int, ActorSystem] = new HashMap[Int, ActorSystem]();
  var ma: Map[Int, Address] = new HashMap[Int, Address]();
  var sysName = "121000005l35120456.node1"
  def startSystem(cout: Int): String = {
    var rs = "{\"status\":\"failed\"}"

    try {
      var i = 1
      for (i <- 1 to cout) {
        if (!m.contains(i)) {
          val nd_system = ActorSystem(systemName)
          Cluster(nd_system).join(joinAddress)
          nd_system.actorOf(Props[MemberListener], name_MemberListener)
          //等待网络稳定
          Thread.sleep(5000)
          nd_system.actorOf(Props[IModuleManager], name_PeerManager + i.toString)
          m += i -> nd_system
          ma += i -> Cluster(nd_system).selfAddress
        }
      }
      rs = "{\"status\":\"success\"}"
    } catch {
      case e: Exception => {
        e.printStackTrace();
      }
    }
    rs
  }

  def stopSystem(from: Int, to: Int): String = {
    var rs = "{\"status\":\"failed\"}"

    try {
      if (from > 0) {
        if (to > from) {
          var i = 0;
          for (i <- from to to) {
            if (m.contains(i) && ma.contains(i)) {
              var ns: ActorSystem = m(i)
              var ad = ma(i)
              if (ns != null) {
                Cluster(ns).down(ad)
                m.remove(i)
                ma.remove(i)
              }
            }
          }
        } else {
          if (m.contains(from) && ma.contains(from)) {
            var ns: ActorSystem = m(from)
            var ad = ma(from)
            if (ns != null) {
              Cluster(ns).down(ad)
              m.remove(from)
              ma.remove(from)
            }
          }
        }
      }
      rs = "{\"status\":\"success\"}"
    } catch {
      case e: Exception => {
        e.printStackTrace();
      }
    }
    rs
  }

  def main(args: Array[String]): Unit = {
    //为方便调试在同一主机join多个node,真实系统同一主机只有一个node,一个EventServer
    //TODO kami 无法真实模拟多System的情况（现阶段各System共用一个单例空间和内存空间。至少应该达到在逻辑上实现分离的情况）
    var nodelist : Array[String] = new Array[String] (5)
    nodelist(0) = "121000005l35120456.node1"
    nodelist(1) = "12110107bi45jh675g.node2"
    nodelist(2) = "122000002n00123567.node3"
    nodelist(3) = "921000005k36123789.node4"
    nodelist(4) = "921000006e0012v696.node5"
    var nodeports : Array[Int] = new Array[Int](5)
    nodeports(0) = 22522
    nodeports(1) = 22523
    nodeports(2) = 22524
    nodeports(3) = 22525
    nodeports(4) = 22526



//    for(i <- 0 to 4) {
//      Thread.sleep(5000)
//      RepChainMgr.Startup4Multi(new StartParameter(nodelist(i),Some(nodeports(i)),None))
//    }

//    val myConfig =
//      ConfigFactory.parseString("akka.remote.artery.ssl.config-ssl-engine.key-store = \"jks/" + "121000005l35120456.node1" +
//        ".jks\"")
//    val regularConfig = getUserCombinedConf("conf/system.conf")
//    val combined =
//      myConfig.withFallback(regularConfig)
//    val complete =
//      ConfigFactory.load(combined)
//
//    val actorSys = ActorSystem("RepChain", complete)


//    var sys1 = new ClusterSystem(nodelist(0), InitType.MULTI_INIT, true)
//    sys1.init
//    val joinAddress = sys1.getClusterAddr //获取组网地址
//    sys1.joinCluster(joinAddress) //加入网络
//    sys1.enableWS() //开启API接口
//    sys1.start //启动系统

//    var sys2 = new ClusterSystem(nodelist(1), InitType.MULTI_INIT, true)
//    sys2.init
//    val joinAddress2 = sys2.getClusterAddr //获取组网地址
//    sys2.joinCluster(joinAddress2) //加入网络
//    sys2.enableWS() //开启API接口
//    sys2.start //启动系统

    for(i <- 0 to 4) {
      Thread.sleep(5000)
      RepChainMgr.Startup4Multi(new StartParameter(nodelist(i),Some(nodeports(i)),None))
    }

    var actorSys = GlobalUtils.systems(0)
    val ml = actorSys.actorOf(TransactionDispatcher.props("td"),  "td")
    val pm = actorSys.actorOf(ModuleManagerOfCFRD.props("modulemanagerraft", "121000005l35120456.node1", false, false, false), "modulemanagerraft")
    val tp = actorSys.actorOf(TransactionPoolOfCFRD.props("tpraft"), "tpraft")
    val ph = actorSys.actorOf(PeerHelper.props("ph"), "ph")
    val blocker = actorSys.actorOf(BlockerOfCFRD.props("blocker"), "blocker")
    val vote = actorSys.actorOf(VoterOfCFRD.props("voter"), "voter")
//    val tp = actorSys.actorOf(TransactionChecker.props("tr"), "tr")

    println("Test Peer Helper")
//    ph ! TestInvoke
//    Thread.sleep(5000)
//    var chaincode:ChaincodeId = new ChaincodeId("ContractAssetsTPL",1)
//    val si = scala.io.Source.fromFile("api_req/json/transfer_" + "invoke" + ".json", "UTF-8")
//    val li = try si.mkString finally si.close()
//    val t12: Transaction = createTransactionByInput("121000005l35120456.node1", chaincode, "transfer", Seq(li))
//    println("Test Peer Helper Id: " + t12.id)
//    val tt12 = TestInvoke2(t12)
//    ph ! tt12
//    Thread.sleep(5000)

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

    val db = ImpDataAccess.GetDataAccess("121000005l35120456.node1")

    val cid2 = ChaincodeId(SystemProfile.getAccountChaincodeName, 1)
    val cid1 = ChaincodeId("ContractPointsTPL", 1)

//    val cid3 = ChaincodeId("TestTPL", 1)
//    val s3 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/TestTPL.scala")
//    val l3 = try s3.mkString finally s3.close()
//    Thread.sleep(5000)
//    println("DEPLOY CONTRACT 1")
//    //生成deploy交易
//    val tttt = PeerHelper.createTransaction4Deploy("121000005l35120456.node1", cid3, l3, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
//    val msg_tttt = DoTransaction(tttt, "dbnumber", TypeOfSender.FromAPI)
//    ml ! msg_tttt
//    tp ! tttt

    Thread.sleep(5000)
    println("DEPLOY CONTRACT 1")
    //生成deploy交易
    val t1 = PeerHelper.createTransaction4Deploy("121000005l35120456.node1", cid1, l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send1 = DoTransaction(t1, "dbnumber", TypeOfSender.FromAPI)
    ml ! msg_send1
//    tp ! t1
    val tt1 = TestInvoke2(t1)
    ph ! tt1

    // 异步
    Thread.sleep(5000)
    println("DEPLOY CONTRACT 2")
    val t2 = PeerHelper.createTransaction4Deploy(sysName, cid2, l2, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send2 = DoTransaction(t2, "dbnumber", TypeOfSender.FromAPI)
    ml ! msg_send2
//    tp ! t2
    val tt2 = TestInvoke2(t2)
    ph ! tt2

    Thread.sleep(5000)
//     生成invoke交易
//     注册账户
    println("SIGN UP SIGNER")
    val t3 = PeerHelper.createTransaction4Invoke(sysName, cid2, ACTION.SignUpSigner, Seq(toJson(signer)))
    val msg_send3 = DoTransaction(t3, "dbnumber", TypeOfSender.FromAPI)
    ml ! msg_send3
//    tp ! t3
    val tt3 = TestInvoke2(t3)
    ph ! tt3

    Thread.sleep(5000)
//     注册证书
    println("SIGN UP CERT")
    val t4 = PeerHelper.createTransaction4Invoke(sysName, cid2, ACTION.SignUpCert, Seq(toJson(certinfo)))
    val msg_send4 = DoTransaction(t4, "dbnumber", TypeOfSender.FromAPI)
    ml ! msg_send4
//    tp ! t4
    val tt4 = TestInvoke2(t4)
    ph ! tt4

    Thread.sleep(5000)
    println("TOP")
    //生成invoke交易
//    var top = TopUp("121000005l35120456", "12110107bi45jh675g", 50)
    var top = TopUp("121000005l35120456", "121000005l35120456", 50)
    //    val t5 = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.set, Seq(sms))
    val t5 = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.set, Seq(toJson(top)))
    val msg_send5 = DoTransaction(t5, "dbnumber", TypeOfSender.FromAPI)
    ml ! msg_send5
//    tp ! t5
    val tt5 = TestInvoke2(t5)
    ph ! tt5

    Thread.sleep(5000)
    println("Unauthoried TOP")
    // 非管理员充值
    var top2 = TopUp("12110107bi45jh675g", "12110107bi45jh675g", 1000)
    val test_top = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.set, Seq(toJson(top2)))
    val msg = DoTransaction(test_top, "dbnumber", TypeOfSender.FromAPI)
    ml ! msg
//    tp ! test_top
    val tttop = TestInvoke2(test_top)
    ph ! tttop

    //println(recv.result)
    Thread.sleep(5000)
    println("TRANSFER")
    //获得提醒文本
    for(i <- 0 to 6) {
      Thread.sleep(5000)
      val rstr =s"""确定签名从本人所持有的账户【121000005l35120456】
          向账户【12110107bi45jh675g】
          转账【3】元"""
      var p = Transfer("121000005l35120456", "12110107bi45jh675g", 3, rstr)
      var t = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.transfer, Seq(toJson(p)))
      var msg_send = DoTransaction(t, "dbnumber", TypeOfSender.FromAPI)
      ml ! msg_send
//      tp ! t
      val ttt: TestInvoke2 = TestInvoke2(t)
      ph ! ttt

      Thread.sleep(5000)
      // new
      // 获取余额
      val t6 = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.get, Seq(toJson("121000005l35120456")))
      val msg_send6 = DoTransaction(t6, "dbnumber", TypeOfSender.FromAPI)
      ml ! msg_send6
//      tp ! t6
      val tt6 = TestInvoke2(t6)
      ph ! tt6
    }
  }
}
