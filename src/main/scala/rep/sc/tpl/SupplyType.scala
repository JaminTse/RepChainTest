package rep.sc.tpl

import scala.collection.mutable.Map

/**
 * 分账接口
 */
trait ISupplySplit {
  /**
   * @param sr 销售收入
   * @param accounts 参与分账的账户
   * @return 分账结果
   */
  def split(sr: Int, accounts: Array[String]): Array[Int]
}


package object SupplyType {
    object ACTION {
      val SignShare = "SignShare"
      val SignFixed = "SignFixed"
      val ConfirmSign = "ConfirmSign"
      val CancelSign = "CancelSign"
      val Split = "Split"
      
    }
    
    object TPL {
      val Share = "Share"
      val Fixed = "Fixed"
    }
    /**
     * 按销售收入分段分成/固定值 的设置项
     */
    case class ShareRatio(from: Int, to: Int, ratio: Double, fixed: Int)    
    //多个账户的分段分成定义
    type ShareMap = scala.collection.mutable.Map[String, Array[ShareRatio]]   
    type FixedMap = scala.collection.mutable.Map[String,Double]  
    /**
     * 签署分成合约的输入参数
     * @param account_sale 提交销售数据的账号
     * @param 
     */
    case class IPTSignShare(account_sale :String, product_id: String, account_remain :String, tpl_param: ShareMap)  
    //固定分账比例
    case class IPTSignFixed(account_sale :String, product_id: String, account_remain :String, ratio:Map[String,Double] )  
    //触发分账的输入参数
    //TODO 如何防止重复提交？
    case class IPTSplit(account_sale :String, product_id:String, amount:Int)
    
    case class IPTConfirm(account: String, tx_id:String)
}
