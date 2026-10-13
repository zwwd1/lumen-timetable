package cn.scvtc.campus

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CasAuthDiagnosticsTest {
    private fun webResult(value:JSONObject)=JSONObject.quote(value.toString())

    @Test fun untrustedFieldsAndStringFlagsCannotEnterTheLocalRecord() {
        val secret="THIS_MUST_NEVER_BE_LOGGED"
        val incoming=JSONObject().put("state","notice").put("host","cas").put("page","h5").put("route","#/")
            .put("password",secret).put("cookie",secret).put("url",secret)
            .put("modals",JSONArray().put(JSONObject().put("owner","login").put("twoBtn",true)
                .put("password",secret).put("phone",false).put("title",secret)
                .put("body",secret).put("leftCallback","function")))
        val result=requireNotNull(CasAuthDiagnostics.sanitized(webResult(incoming)))
        assertEquals("notice",result.getString("state"))
        assertTrue(result.getJSONArray("modals").getJSONObject(0).getBoolean("twoBtn"))
        assertFalse(result.toString().contains(secret))
        assertFalse(result.has("cookie"));assertFalse(result.has("url"))
    }

    @Test fun malformedOrOversizedResultsDoNotProduceARecord() {
        assertNull(CasAuthDiagnostics.sanitized("not json"))
        assertNull(CasAuthDiagnostics.sanitized("null"))
        assertNull(CasAuthDiagnostics.sanitized("x".repeat(8193)))
    }

    @Test fun unknownRoutesAndExtraModalRowsAreBounded() {
        val rows=JSONArray();repeat(8){rows.put(JSONObject().put("owner","login"))}
        val incoming=JSONObject().put("state","arbitrary page text").put("route","#/private-value").put("modals",rows)
        val result=requireNotNull(CasAuthDiagnostics.sanitized(webResult(incoming)))
        assertEquals("other",result.getString("state"));assertEquals("other",result.getString("route"))
        assertEquals(4,result.getJSONArray("modals").length())
    }
}
