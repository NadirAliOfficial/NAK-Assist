package com.teamnak.nakassist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadDetectorTest {

    private fun hot(vararg msgs: String) = assertTrue(LeadDetector.isHotLead(msgs.toList()))
    private fun cold(vararg msgs: String) = assertFalse(LeadDetector.isHotLead(msgs.toList()))

    @Test fun `dollar budget is a lead`() = hot("I have \$500 for this")
    @Test fun `budget word is a lead`() = hot("What budget do you need?")
    @Test fun `how much is a lead`() = hot("How much for an MT5 EA?")
    @Test fun `deadline is a lead`() = hot("Need it done ASAP")
    @Test fun `ready to order is a lead`() = hot("ok I'm ready to start")
    @Test fun `any message in thread counts`() = hot("hi", "can you send me a custom offer")

    @Test fun `greeting is not a lead`() = cold("hi there")
    @Test fun `thanks is not a lead`() = cold("thanks, got it")
    @Test fun `accurate does not match rate`() = cold("is the model accurate?")
    @Test fun `empty thread is not a lead`() = cold()
}
