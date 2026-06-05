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

fun triggerReferenceUpload(uploadUrl: String): Unit =
    js("(function(){ window.__pendingReferenceUrl=''; window.__referenceUploadStatus='choosing photo…'; var input=document.createElement('input'); input.type='file'; input.accept='image/*'; input.onchange=function(ev){ var file=ev.target.files&&ev.target.files[0]; if(!file){window.__referenceUploadStatus=''; return;} window.__referenceUploadStatus='uploading '+file.name+'…'; var form=new FormData(); form.append('file', file); fetch(uploadUrl,{method:'POST',body:form}).then(function(r){return r.json();}).then(function(d){ if(d&&d.url){window.__pendingReferenceUrl=d.url; window.__referenceUploadStatus='';} else {window.__referenceUploadStatus='upload failed';} }).catch(function(e){window.__referenceUploadStatus='upload error';}); }; input.click(); })()")

fun consumePendingReferenceUrl(): String =
    js("(function(){ var u=window.__pendingReferenceUrl||''; window.__pendingReferenceUrl=''; return u; })()")

fun readReferenceUploadStatus(): String =
    js("window.__referenceUploadStatus || ''")

class StageDebugObservability : Observability {
    override fun onRender(nodeId: String) {}

    override fun onInteraction(nodeId: String, action: ActionRef, resolvedParams: Map<String, Any?>) {
        pushStageLog("spec-interaction node=$nodeId action=${action.action} params=$resolvedParams")
    }
}
