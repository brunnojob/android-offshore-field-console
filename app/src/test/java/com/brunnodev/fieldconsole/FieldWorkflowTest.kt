package com.brunnodev.fieldconsole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FieldWorkflowTest {
    private val tasks = listOf(ChecklistTask("isolate", "Confirm isolation", true), ChecklistTask("photo", "Attach evidence", false))
    @Test fun mandatoryTasksGateSubmission() {
        val workflow = InspectionWorkflow(tasks)
        assertThrows(IllegalStateException::class.java) { workflow.submit() }
        workflow.complete("isolate")
        workflow.submit()
        assertEquals(InspectionState.SUBMITTED, workflow.state)
    }
    @Test fun qrPrefixIsNormalizedAndInvalidTagsAreRejected() {
        assertEquals("PUMP-204", parseAssetTag("RF:PUMP-204"))
        assertThrows(IllegalArgumentException::class.java) { parseAssetTag("PUMP 204") }
    }
}
