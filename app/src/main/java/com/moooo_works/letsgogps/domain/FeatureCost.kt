package com.moooo_works.letsgogps.domain

/**
 * 計次制功能的價目表。
 *
 * 日後新增採計次制的功能時，在這裡加一個常數即可，不需改動閘門或錢包的架構。
 * 注意：既有的 Pro 功能（路線、搖桿、匯入匯出）走的是「看廣告解鎖 6 小時」
 * 的時數機制，不在這張表上。
 */
object FeatureCost {

    /** 步數同步：一次模擬 session 消耗 1 次。 */
    const val STEP_SYNC_SESSION = 1

    /** 看一支獎勵廣告可獲得的次數。 */
    const val CREDITS_PER_REWARDED_AD = 1
}
