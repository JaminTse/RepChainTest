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

package rep.network.sync

import akka.actor.{ ActorRef, Props }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.network.base.ModuleBase
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType }
import rep.storage.ImpDataAccess
import rep.protos.peer._
import rep.network.util.NodeHelp
import rep.network.sync.SyncMsg.{ResponseInfo,BlockDataOfResponse}
import rep.log.RepLogger

object SynchronizeResponser {
  def props(name: String): Props = Props(classOf[SynchronizeResponser], name)
}

class SynchronizeResponser(moduleName: String) extends ModuleBase(moduleName) {

  import context.dispatcher
  import scala.concurrent.duration._
  import scala.util.control.Breaks._

  override def preStart(): Unit = {
    SubscribeTopic(mediator, self, selfAddr, BlockEvent.CHAIN_INFO_SYNC, true)
    RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix( "SynchronizeResponse start"))
  }

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)

  override def receive: Receive = {
    case SyncMsg.ChainInfoOfRequest =>
      if (NodeHelp.isSameNodeForRef(sender(), self)) {
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(  s"recv sync chaininfo request,it is self,do not response, from actorAddr" + "～" + NodeHelp.getNodePath(sender())))
      } else {
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix( s"recv sync chaininfo request from actorAddr" + "～" + NodeHelp.getNodePath(sender())))
        val responseInfo = dataaccess.getBlockChainInfo()
        sender ! ResponseInfo(responseInfo,self)
      }

    case SyncMsg.BlockDataOfRequest(startHeight) =>
      sendEvent(EventType.PUBLISH_INFO, mediator,sender.path.toString(), selfAddr,  Event.Action.BLOCK_SYNC)
      sendEventSync(EventType.PUBLISH_INFO, mediator,sender.path.toString(), selfAddr,  Event.Action.BLOCK_SYNC)
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(  s"node number:${pe.getSysTag},start block number:${startHeight},Get a data request from  $sender" + "～" + selfAddr))
      val local = dataaccess.getBlockChainInfo()
      var data = Block()
      if (local.height >= startHeight) {
        data = dataaccess.getBlock4ObjectByHeight(startHeight)
        sender  ! SyncMsg.BlockDataOfResponse(data)
      }

  }

}