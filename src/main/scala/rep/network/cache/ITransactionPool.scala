package rep.network.cache

import java.util.concurrent.{ExecutorService, Executors}

import akka.actor.{Address, Props}
import rep.app.conf.SystemProfile
import rep.crypto.cert.SignTool
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.cache.ITransactionPool.CheckedTransactionResult
import rep.network.tools.transpool.TransactionPoolMgr
import rep.network.util.NodeHelp
import rep.protos.peer.{Event, Transaction}
import rep.storage.ImpDataAccess
import rep.utils.ActorUtils
import rep.utils.GlobalUtils.EventType

/**
 * Created by jiangbuyun on 2020/03/19.
 * 抽象的交易池actor
 */

object ITransactionPool{
  def props(name: String): Props = Props(classOf[ITransactionPool], name)
  //交易检查结果
  case class CheckedTransactionResult(result: Boolean, msg: String)
}

abstract class ITransactionPool (moduleName: String) extends ModuleBase(moduleName) {
  import akka.actor.ActorSelection

  private val transPoolActorName = "/user/modulemanager/transactionpool"
  private var addr4NonUser = ""
  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
  protected var works : ExecutorService = Executors.newFixedThreadPool(5)
  var poolIsEmpty = pe.getTransPoolMgr.isEmpty
  private var txCountOfThrow = 0
  private var txCount = 0

  override def preStart(): Unit = {
    //注册接收交易的广播
    SubscribeTopic(mediator, self, selfAddr, Topic.Transaction, true)
  }

  def toAkkaUrl(sn: Address, actorName: String): String = {
    return sn.toString + "/" + actorName;
  }

  def visitStoreService(sn: Address, actorName: String, t1: Transaction) = {
    try {
      val selection: ActorSelection = context.actorSelection(toAkkaUrl(sn, actorName));
      selection ! t1
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }

  /**
   * 检查交易是否符合规则
   * @param t
   * @param dataAccess
   * @return
   */
  def checkTransaction(t: Transaction, dataAccess: ImpDataAccess): CheckedTransactionResult = {
    var resultMsg = ""
    var result = false

    //if(SystemProfile.getHasPreloadTransOfApi){
      val sig = t.getSignature
      val tOutSig = t.clearSignature //t.withSignature(null)
      val cert = sig.getCertId

      try {
        val siginfo = sig.signature.toByteArray()

        if (SignTool.verify(siginfo, tOutSig.toByteArray, cert, pe.getSysTag)) {
          if (pe.getTransPoolMgr.findTrans(t.id) || dataAccess.isExistTrans4Txid(t.id)) {
            resultMsg = s"The transaction(${t.id}) is duplicated with txid"
          } else {
            result = true
          }
        } else {
          resultMsg = s"The transaction(${t.id}) is not completed"
        }
      } catch {
        case e: RuntimeException => throw e
      }
    //}else{
    //  result = true
    //}

    CheckedTransactionResult(result, resultMsg)
  }

  protected def sendVoteMessage:Unit

  private def addTransToCache(t: Transaction) = {
    val checkedTransactionResult = checkTransaction(t, dataaccess)
    //签名验证成功
    val poolIsEmpty = pe.getTransPoolMgr.isEmpty
    if((checkedTransactionResult.result) && (SystemProfile.getMaxCacheTransNum == 0 || pe.getTransPoolMgr.getTransLength() < SystemProfile.getMaxCacheTransNum) ){
      pe.getTransPoolMgr.putTran(t, pe.getSysTag)
      RepLogger.trace(RepLogger.System_Logger,this.getLogMsgPrefix(s"${pe.getSysTag} trans pool recv,txid=${t.id}"))
      //广播接收交易事件
      //if (pe.getTransPoolMgr.getTransLength() >= SystemProfile.getMinBlockTransNum)
      if (poolIsEmpty)//加入交易之前交易池为空，发送抽签消息
        sendVoteMessage
    }
  }

  private def publishTrans(t: Transaction) = {
    if (this.addr4NonUser == "" && this.selfAddr.indexOf("/user") > 0) {
      this.addr4NonUser = this.selfAddr.substring(0, this.selfAddr.indexOf("/user"))
    }

    pe.getNodeMgr.getStableNodes.foreach(f => {
      if (this.addr4NonUser != "" && !NodeHelp.isSameNode(f.toString, this.addr4NonUser)) {
        visitStoreService(f, this.transPoolActorName, t)
      }

    })
  }

  private def TransactionHandler(t:Transaction)={
    this.poolIsEmpty = pe.getTransPoolMgr.isEmpty
    this.works.execute(new TransactionPoolWorker(pe.getTransPoolMgr,t, dataaccess))
    if(this.poolIsEmpty){
      if(!pe.getTransPoolMgr.isEmpty) {
        sendVoteMessage
      }
    }
  }

  private def addTransToPool(t: Transaction) = {
    this.txCount = this.txCount + 1
    this.poolIsEmpty = pe.getTransPoolMgr.isEmpty
    if((SystemProfile.getMaxCacheTransNum == 0 || pe.getTransPoolMgr.getTransLength() < SystemProfile.getMaxCacheTransNum) ){
      pe.getTransPoolMgr.putTran(t, pe.getSysTag)
      RepLogger.trace(RepLogger.System_Logger,this.getLogMsgPrefix(s"${pe.getSysTag} trans pool recv,txid=${t.id}"))
      if (poolIsEmpty){//加入交易之前交易池为空，发送抽签消息
        sendVoteMessage
      }
    }else{
      this.txCountOfThrow = this.txCountOfThrow + 1
      RepLogger.trace(RepLogger.System_Logger,this.getLogMsgPrefix(s"${pe.getSysTag} trans pool is full,txid=${t.id},txCount=${this.txCount},txCountThrow=${this.txCountOfThrow}"))
    }
  }

  override def receive = {
    //处理接收的交易
    case t: Transaction =>
      //保存交易到本地
      sendEvent(EventType.RECEIVE_INFO, mediator, pe.getSysTag, Topic.Transaction, Event.Action.TRANSACTION)
      //addTransToCache(t)
      //System.out.println(s"outputer : ${pe.getSysTag},entry verify transaction ,from:${this.sender().toString()}")
      if(SystemProfile.getIsUseValidator){
        addTransToPool(t)
//        println("add transaction to pool successfully")
      }else{
//        println("enter transaction handler")
        TransactionHandler(t)
      }
    case _ => //ignore
  }
}
