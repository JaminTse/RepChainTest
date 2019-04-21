package rep.network.consensus.block

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ Actor, ActorRef, Props, Address, ActorSelection }
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.network.base.ModuleBase
import rep.network.consensus.endorse.EndorseMsg.{ EndorsementInfo, ResultOfEndorsed, RequesterOfEndorsement, ResultOfEndorseRequester, ResultFlagOfEndorse }
import rep.network.tools.PeerExtension
import rep.network.Topic
import rep.protos.peer._
import rep.utils._
import akka.pattern.AskTimeoutException
import rep.network.consensus.util.BlockVerify
import scala.util.control.Breaks
import rep.utils.GlobalUtils.{ EventType, ActorType }
import rep.network.sync.SyncMsg.StartSync
import rep.log.RepLogger

object EndorsementRequest4Future {
  def props(name: String): Props = Props(classOf[EndorsementRequest4Future], name)
}

class EndorsementRequest4Future(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.collection.breakOut
  import scala.concurrent.duration._

  implicit val timeout = Timeout(TimePolicy.getTimeoutEndorse seconds)
  private val endorsementActorName = "/user/modulemanager/endorser"

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "EndorsementRequest4Future Start"))
  }

  private def ExecuteOfEndorsement(addr: Address, data: EndorsementInfo): ResultOfEndorsed = {
    try {
      val selection: ActorSelection = context.actorSelection(toAkkaUrl(addr, endorsementActorName));
      val future1 = selection ? data
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "--------ExecuteOfEndorsement waiting result"))
      Await.result(future1, timeout.duration).asInstanceOf[ResultOfEndorsed]
    } catch {
      case e: AskTimeoutException =>
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "--------ExecuteOfEndorsement timeout"))
        null
      case te: TimeoutException =>
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "--------ExecuteOfEndorsement java timeout"))
        null
    }
  }

  private def toAkkaUrl(addr: Address, actorName: String): String = {
    return addr.toString + "/" + actorName;
  }

  private def EndorsementVerify(block: Block, result: ResultOfEndorsed): Boolean = {
    val bb = block.clearEndorsements.toByteArray
    val ev = BlockVerify.VerifyOneEndorseOfBlock(result.endor, bb, pe.getSysTag)
    if (ev._1) {
      true
    } else {
      false
    }
  }

  private def handler(reqinfo: RequesterOfEndorsement) = {
    schedulerLink = clearSched()
    val result = this.ExecuteOfEndorsement(reqinfo.endorer, EndorsementInfo(reqinfo.blc, reqinfo.blocker))
    if (result != null) {
      if (result.result == ResultFlagOfEndorse.success) {
        if (EndorsementVerify(reqinfo.blc, result)) {
          val re = ResultOfEndorseRequester(true, result.endor, result.BlockHash, reqinfo.endorer)
          context.parent ! re
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "--------endorsementRequest4Future recv endorsement result is success "))
        } else {
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("--------endorsementRequest4Future recv endorsement result is error, result=${result.result}"))
          context.parent ! ResultOfEndorseRequester(false, null, reqinfo.blc.hashOfBlock.toStringUtf8(), reqinfo.endorer)
        }
      } else {
        if (result.result == ResultFlagOfEndorse.BlockHeightError) {
          if (result.endorserOfChainInfo.height > pe.getCurrentHeight + 1) {
            pe.getActorRef(ActorType.synchrequester) ! StartSync(false)
            context.parent ! ResultOfEndorseRequester(false, null, reqinfo.blc.hashOfBlock.toStringUtf8(), reqinfo.endorer)
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "--------endorsementRequest4Future recv endorsement result must synch "))
          } else {
            schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutEndorse seconds, self, RequesterOfEndorsement(reqinfo.blc, reqinfo.blocker, reqinfo.endorer))
          }
        } else {
          context.parent ! ResultOfEndorseRequester(false, null, reqinfo.blc.hashOfBlock.toStringUtf8(), reqinfo.endorer)
        }
      }
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("--------endorsementRequest4Future recv endorsement result is null "))
      context.parent ! ResultOfEndorseRequester(false, null, reqinfo.blc.hashOfBlock.toStringUtf8(), reqinfo.endorer)
    }
  }

  override def receive = {
    case RequesterOfEndorsement(block, blocker, addr) =>
      handler(RequesterOfEndorsement(block, blocker, addr))

    //case ResultOfEndorsed(result, endor, blockhash)=>
    //  handlerOfResult(ResultOfEndorsed(result, endor, blockhash))
    case _ => //ignore
  }
}