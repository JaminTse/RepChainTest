package rep.network.confirmblock.raft

import java.util.concurrent.{ExecutorService, Executors}

import akka.actor.{ActorRef, Props}
import rep.app.conf.SystemProfile
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.confirmblock.{BoardcastConfirmBlock1, IConfirmOfBlock}
import rep.network.consensus.common.MsgOfConsensus.{BatchStore, BlockRestore, ConfirmedBlock}
import rep.network.module.ModuleActorType
import rep.network.persistence.IStorager.SourceOfBlock
import rep.network.util.NodeHelp
import rep.protos.peer.{Block, Event}
import rep.utils.GlobalUtils.EventType

/**
 * Created by jiangbuyun on 2020/03/19.
 * RAFT共识的确认块actor
 */

object ConfirmBlockOfRAFT{
  def props(name: String): Props = Props(classOf[ConfirmBlockOfRAFT], name)
}

class ConfirmBlockOfRAFT(moduleName: String) extends IConfirmOfBlock(moduleName: String) {
  protected var works : ExecutorService = Executors.newFixedThreadPool(1)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("ConfirmBlockOfRAFT module start"))
    if (SystemProfile.getVoteNodeList.contains(pe.getSysTag)) {
      SubscribeTopic(mediator, self, selfAddr, Topic.Block, false)
    }
  }

  private def broadcastConfirmBlock(block: Block, actRefOfBlock: ActorRef):Unit={
    if (SystemProfile.getVoteNodeList.contains(pe.getSysTag) && SystemProfile.getIsForwarding && block.height > pe.getCurrentHeight) {
      var confirms = new BoardcastConfirmBlock1(context, ConfirmedBlock(block, actRefOfBlock))
      this.works.execute(confirms)
    }
  }

  override protected def handler(block: Block, actRefOfBlock: ActorRef) = {
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify endorsement start,height=${block.height}"))
    if (SystemProfile.getIsVerifyOfEndorsement) {
      if (asyncVerifyEndorses(block)) {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify endorsement end,height=${block.height}"))
        //背书人的签名一致
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify endorsement sort,height=${block.height}"))
        pe.getBlockCacheMgr.addToCache(BlockRestore(block, SourceOfBlock.CONFIRMED_BLOCK, actRefOfBlock))
        pe.getActorRef(ModuleActorType.ActorType.storager) ! BatchStore
        sendEvent(EventType.RECEIVE_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.BLOCK_NEW)
        broadcastConfirmBlock(block,actRefOfBlock)
      } else {
        //背书验证有错误
        RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify endorsement error,height=${block.height}"))
      }
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify endorsement sort,height=${block.height}"))
      pe.getBlockCacheMgr.addToCache(BlockRestore(block, SourceOfBlock.CONFIRMED_BLOCK, actRefOfBlock))
      pe.getActorRef(ModuleActorType.ActorType.storager) ! BatchStore
      sendEvent(EventType.RECEIVE_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.BLOCK_NEW)
      broadcastConfirmBlock(block,actRefOfBlock)
    }
  }

  override protected def checkedOfConfirmBlock(block: Block, actRefOfBlock: ActorRef) = {
    if (pe.getCurrentBlockHash == "" && block.previousBlockHash.isEmpty()) {
      //if (NodeHelp.isSeedNode(pe.getNodeMgr.getStableNodeName4Addr(actRefOfBlock.path.address))) {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify blockhash,height=${block.height}"))
        handler(block, actRefOfBlock)
      //}else{
      //  RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"not confirm genesis block,blocker is not seed node,height=${block.height}"))
      //}
    } else {
      //与上一个块一致
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify blockhash,height=${block.height}"))
      handler(block, actRefOfBlock)
      pe.setConfirmHeight(block.height)

    }
  }
}
