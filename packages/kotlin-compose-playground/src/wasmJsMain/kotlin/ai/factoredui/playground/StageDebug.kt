package ai.factoredui.playground

import ai.factoredui.compose.observability.Observability
import ai.factoredui.compose.schema.ActionRef

fun publishStageBindings(snapshot: String): Unit =
    js("window.__stageBindings = snapshot")

fun pushStageLog(line: String): Unit =
    js("(window.__stageLog = window.__stageLog || []).push(line)")

fun publishStageLastAction(json: String): Unit =
    js("window.__stageLastAction = json")

fun pushStageTrainingRow(json: String): Unit =
    js("(window.__stageTrainingRows = window.__stageTrainingRows || []).push(json)")

fun nowMillis(): Double =
    js("Date.now()")

fun installStageTestHooks(): Unit =
    js("(function(){ window.__stageSet = function(path, value){ (window.__stageSetQueue = window.__stageSetQueue || []).push({path: path, value: value}); return 'queued ' + path + '=' + value; }; window.__stageGoto = function(name){ window.__stageGotoPending = name; return 'goto ' + name; }; })()")

fun consumeStageSetQueue(): String =
    js("(function(){ var q = window.__stageSetQueue || []; window.__stageSetQueue = []; return JSON.stringify(q); })()")

fun consumeStageGoto(): String =
    js("(function(){ var g = window.__stageGotoPending || ''; window.__stageGotoPending = ''; return g; })()")

fun triggerReferenceUpload(uploadUrl: String): Unit =
    js("(function(){ window.__pendingReferenceUrl=''; window.__referenceUploadStatus='choosing photo…'; var input=document.createElement('input'); input.type='file'; input.accept='image/*'; input.style.display='none'; document.body.appendChild(input); input.onchange=function(ev){ var file=ev.target.files&&ev.target.files[0]; if(!file){window.__referenceUploadStatus=''; if(input.parentNode){document.body.removeChild(input);} return;} window.__referenceUploadStatus='uploading '+file.name+'…'; var form=new FormData(); form.append('file', file); fetch(uploadUrl,{method:'POST',body:form}).then(function(r){ if(!r.ok){throw new Error('HTTP '+r.status);} return r.json(); }).then(function(d){ if(d&&d.url){window.__pendingReferenceUrl=d.url; window.__referenceUploadStatus='photo uploaded ✓';} else {window.__referenceUploadStatus='upload failed: no url in response';} }).catch(function(e){window.__referenceUploadStatus='upload error: '+(e&&e.message?e.message:e);}).finally(function(){ if(input.parentNode){document.body.removeChild(input);} }); }; input.click(); })()")

fun consumePendingReferenceUrl(): String =
    js("(function(){ var u=window.__pendingReferenceUrl||''; window.__pendingReferenceUrl=''; return u; })()")

fun readReferenceUploadStatus(): String =
    js("window.__referenceUploadStatus || ''")

fun setReferenceUploadStatus(message: String): Unit =
    js("window.__referenceUploadStatus = message")

class StageDebugObservability : Observability {
    override fun onRender(nodeId: String) {}

    override fun onInteraction(nodeId: String, action: ActionRef, resolvedParams: Map<String, Any?>) {
        pushStageLog("spec-interaction node=$nodeId action=${action.action} params=$resolvedParams")
    }
}
