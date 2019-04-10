package rep.network.consensus.block

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ ActorRef, Address, Props }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.google.protobuf.ByteString
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.crypto.Sha256
import rep.network._
import rep.network.base.ModuleBase
import rep.network.consensus.block.Blocker.{ConfirmedBlock,PreTransBlock,PreTransBlockResult}
import rep.network.cluster.ClusterHelper
import rep.network.consensus.CRFD.CRFD_STEP
import rep.protos.peer._
import rep.storage.ImpDataAccess
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType, NodeStatus }
import scala.collection.mutable
import com.sun.beans.decoder.FalseElementHandler
import scala.util.control.Breaks
import rep.log.trace.LogType
import rep.utils.IdTool
import scala.util.control.Breaks._
import rep.network.consensus.util.{ BlockHelp, BlockVerify }
import rep.network.util.NodeHelp

object GenesisBlocker {
  def props(name: String): Props = Props(classOf[GenesisBlocker], name)

  case object GenesisBlock

}

/**
 * 出块模块
 *
 * @author shidianyue
 * @version 1.0
 * @since 1.0
 * @param moduleName 模块名称
 */
class GenesisBlocker(moduleName: String) extends ModuleBase(moduleName) {

  import context.dispatcher
  import scala.concurrent.duration._
  import akka.actor.ActorSelection
  import scala.collection.mutable.ArrayBuffer
  import rep.protos.peer.{ Transaction }

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload seconds)

  var preblock: Block = null

  override def preStart(): Unit = {
    logMsg(LogType.INFO, "Block module start")
    SubscribeTopic(mediator, self, selfAddr, Topic.Block, true)
  }

  

  private def ExecuteTransactionOfBlock(block: Block): Block = {
    try {
      val future = pe.getActorRef(ActorType.PRELOADTRANS_MODULE) ? PreTransBlock(block, "preload")
      val result = Await.result(future, timeout.duration).asInstanceOf[PreTransBlockResult]
      if (result.result) {
        result.blc
      } else {
        null
      }
    } catch {
      case e: AskTimeoutException => null
    }
  }

  override def receive = {
    //创建块请求（给出块人）
    case GenesisBlocker.GenesisBlock =>
      if(dataaccess.getBlockChainInfo().height == 0 && NodeHelp.isSeedNode(pe.getSysTag)){
        logMsg(LogType.INFO, "Create genesis block")
        var blc = BlockHelp.CreateGenesisBlock
        blc = ExecuteTransactionOfBlock(blc)
        if (blc != null) {
          blc = BlockHelp.AddBlockHash(blc)
          blc = BlockHelp.AddSignToBlock(blc, pe.getSysTag)
          //sendEvent(EventType.RECEIVE_INFO, mediator, selfAddr, Topic.Block, Event.Action.BLOCK_NEW)
          mediator ! Publish(Topic.Block, ConfirmedBlock(blc, sender))
          //getActorRef(pe.getSysTag, ActorType.PERSISTENCE_MODULE) ! BlockRestore(blc, SourceOfBlock.CONFIRMED_BLOCK, self)
        } else {
          //创世块失败
        }
      }

    case _ => //ignore
  }

}