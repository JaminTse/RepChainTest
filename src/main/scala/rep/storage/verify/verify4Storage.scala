package rep.storage.verify

import rep.storage.ImpDataAccess
import rep.log.trace.RepLogger
import rep.log.trace.ModuleType
import rep.protos.peer._
import rep.crypto.Sha256
import scala.util.control.Breaks._

object verify4Storage {
  def verify(sysName:String):Boolean={
    var b = true
    var errorInfo = "未知问题"
    try{
      val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(sysName)
      val bcinfo = sr.getBlockChainInfo()
      if(bcinfo != null){
        if(bcinfo.height > 0){
          var prehash = ""
          breakable(
          for(i <- 1 to bcinfo.height.toInt){
            val block = sr.getBlock4ObjectByHeight(i)
            if(block == null){
              errorInfo = "第"+i+"块信息无法获取，区块文件可能损坏。"
              b = false
              break
            }else{
              if(!prehash.equalsIgnoreCase("")){
                if(!prehash.equals(block.previousBlockHash.toStringUtf8())){
                  errorInfo = "第"+i+"块信息错误，区块文件可能被篡改。"
                  b = false
                  break
                }
              }
              val rbb = block.toByteArray
              prehash = Sha256.hashstr(rbb);
            }
          })
        }
      }else{
        errorInfo = "无法获取链信息，LevelDB可能损坏。"
        b = false
      }
    }catch{
      case e:Exception =>{
        RepLogger.logError4Exception(sysName, ModuleType.storager, "系统自检错误：存储检查失败，LevelDB或者Block文件损坏，请与管理员联系！错误原因="+errorInfo,e)
        throw new Exception("系统自检错误：存储检查失败，LevelDB或者Block文件损坏，请与管理员联系！错误信息："+errorInfo+",其他信息="+e.getMessage)
      }
    }
    b
  }
  
}