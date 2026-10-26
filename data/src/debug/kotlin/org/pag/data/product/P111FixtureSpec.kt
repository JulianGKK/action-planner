package org.pag.data.product

/** Synthetic calibration metadata only. Does not configure a product capacity or budget. */
data class P111FixtureSpec(val id:String,val people:Int,val tasks:Int,val roles:Int,val events:Int,val textFactor:Int,val filledPlans:Boolean) {
    companion object {
        fun find(id:String):P111FixtureSpec?=when(id){
            "p111-small"->P111FixtureSpec(id,20,40,6,5000,1,false)
            "p111-medium"->P111FixtureSpec(id,100,250,6,5000,1,true)
            "p111-large6"->P111FixtureSpec(id,200,1000,6,5000,1,true)
            "p111-large32"->P111FixtureSpec(id,200,1000,32,5000,1,true)
            "p111-large100"->P111FixtureSpec(id,200,1000,100,5000,1,true)
            "p111-large6-long5k"->P111FixtureSpec(id,200,1000,6,5000,15,true)
            "p111-large32-long5k"->P111FixtureSpec(id,200,1000,32,5000,15,true)
            "p111-large100-long5k"->P111FixtureSpec(id,200,1000,100,5000,15,true)
            "p111-large6-128k"->P111FixtureSpec(id,200,1000,6,128000,15,true)
            "p111-large32-128k"->P111FixtureSpec(id,200,1000,32,128000,15,true)
            "p111-large100-128k"->P111FixtureSpec(id,200,1000,100,128000,15,true)
            "p111-single-long-person"->P111FixtureSpec(id,20,40,6,5000,1,false)
            "p111-unicode"->P111FixtureSpec(id,20,40,6,5000,256,true)
            else->null
        }
    }
}
