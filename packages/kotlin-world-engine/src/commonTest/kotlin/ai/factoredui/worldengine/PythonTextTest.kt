package ai.factoredui.worldengine

import ai.factoredui.worldengine.text.pythonFloatRepr
import ai.factoredui.worldengine.text.pythonJsonDumps
import ai.factoredui.worldengine.text.pythonRepr
import ai.factoredui.worldengine.text.pythonStr
import ai.factoredui.worldengine.text.pythonStrRepr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class PythonTextTest {
    @Test
    fun floats_print_as_python_repr_switching_to_exponent_outside_the_fixed_window() {
        assertEquals("4.0", pythonFloatRepr(4.0))
        assertEquals("0.1", pythonFloatRepr(0.1))
        assertEquals("0.0001", pythonFloatRepr(0.0001))
        assertEquals("1e-05", pythonFloatRepr(0.00001))
        assertEquals("1.5e-07", pythonFloatRepr(1.5e-7))
        assertEquals("1000000000000000.0", pythonFloatRepr(1e15))
        assertEquals("1e+16", pythonFloatRepr(1e16))
        assertEquals("12345678.9", pythonFloatRepr(12345678.9))
        assertEquals("-0.0", pythonFloatRepr(-0.0))
        assertEquals("inf", pythonFloatRepr(Double.POSITIVE_INFINITY))
        assertEquals("nan", pythonFloatRepr(Double.NaN))
        assertEquals("0.30000000000000004", pythonFloatRepr(0.1 + 0.2))
    }

    @Test
    fun strings_repr_with_single_quotes_unless_they_hold_one() {
        assertEquals("'path'", pythonStrRepr("path"))
        assertEquals("\"$: missing 'id'\"", pythonStrRepr("$: missing 'id'"))
        assertEquals("'a\\'b\"c'", pythonStrRepr("a'b\"c"))
        assertEquals("'tab\\there\\n'", pythonStrRepr("tab\there\n"))
    }

    @Test
    fun json_values_repr_and_str_like_python_objects() {
        val document = Json.parseToJsonElement("""{"a": [1, 2.5, "x"], "b": true, "c": null}""")
        assertEquals("{'a': [1, 2.5, 'x'], 'b': True, 'c': None}", pythonRepr(document))
        assertEquals("twelve", pythonStr(JsonPrimitive("twelve")))
        assertEquals("3", pythonStr(Json.parseToJsonElement("3")))
        assertEquals("300.0", pythonStr(Json.parseToJsonElement("3e2")))
    }

    @Test
    fun json_dumps_sorts_keys_and_uses_python_separators() {
        val parameters = Json.parseToJsonElement("""{"type": "van_pad", "row": 10, "col": 10.0, "name": "café"}""")
        assertEquals("{\"col\": 10.0, \"name\": \"caf\\u00e9\", \"row\": 10, \"type\": \"van_pad\"}", pythonJsonDumps(parameters, sortKeys = true))
        assertEquals("{}", pythonJsonDumps(Json.parseToJsonElement("{}"), sortKeys = true))
    }
}
