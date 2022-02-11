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
import rep.app.conf.SystemProfile
import rep.network.autotransaction.PeerHelper
import rep.network.autotransaction.PeerHelper.{TestInvoke, TestInvoke2, createTransactionByInput}
import rep.network.cluster.MemberListener
import rep.network.module.IModuleManager
import rep.protos.peer.{Certificate, ChaincodeId, Signer, Transaction}
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.{SandboxDispatcher, TransactionDispatcher, TypeOfSender}
import rep.sc.tpl.{CertInfo}
import rep.storage.ImpDataAccess
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
  val printing = "printing"
  val topup = "topup"
  val buy = "buy"
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

    for(i <- 0 to 4) {
      Thread.sleep(5000)
      RepChainMgr.Startup4Multi(new StartParameter(nodelist(i),Some(nodeports(i)),None))
    }

    var actorSys = GlobalUtils.systems(0)
    val td = actorSys.actorOf(TransactionDispatcher.props("td"),  "td")
    val ph = actorSys.actorOf(PeerHelper.props("ph"), "ph")

    // 部署积分管理合约
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

    val s3 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/BusinessTestTPL.scala")
    val l3 = try s3.mkString finally s3.close()
    val cid3 = ChaincodeId("BusinessTestTPL", 1)

    Thread.sleep(5000)
    println("DEPLOY CONTRACT 3")
    val bu = PeerHelper.createTransaction4Deploy(sysName, cid3, l3, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send_bu = DoTransaction(bu, "dbnumber", TypeOfSender.FromAPI)
    td ! msg_send_bu

    Thread.sleep(5000)
    println("TOP")
    //生成invoke交易
    var top = TopUp("121000005l35120456", "12110107bi45jh675g", 50)
    val t5 = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.topup, Seq(toJson(top)))
    val msg_send5 = DoTransaction(t5, "dbnumber", TypeOfSender.FromAPI)
    td ! msg_send5
//    tp ! t5
    val tt5 = TestInvoke2(t5)
    ph ! tt5

    // 测试用例：从他人账户转账
    Thread.sleep(5000)
    println("##########  正在进行转账交易  ########")
    var transfer = Transfer("12110107bi45jh675g", "121000005l35120456", 10, "remind")
    val tr = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.transfer, Seq(toJson(transfer)))
    val msg_sendtr = DoTransaction(tr, "dbnumber", TypeOfSender.FromAPI)
    td ! msg_sendtr
    //    tp ! t5

    // 非管理员充值
    var top2 = TopUp("12110107bi45jh675g", "12110107bi45jh675g", 1000)
    for(i <- 0 to 15) {
      Thread.sleep(5000)
      val test_bu = PeerHelper.createTransaction4Invoke(sysName, cid3, ACTION.printing, Seq(toJson(top2)))
      val test_msg_send = DoTransaction(test_bu, "dbnumber", TypeOfSender.FromAPI)
      td ! test_msg_send
      // tp ! test_top
      // val test_t = TestInvoke2(test_bu)
      // ph ! test_t
    }
    for(i <- 0 to 5) {
      Thread.sleep(5000)
      val test_bu2 = PeerHelper.createTransaction4Invoke(sysName, cid3, ACTION.buy, Seq(toJson(top2)))
      val test_msg_send2 = DoTransaction(test_bu2, "dbnumber", TypeOfSender.FromAPI)
      td ! test_msg_send2
    }
  }
}
