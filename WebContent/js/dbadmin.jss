function setQuery(historyField) {
	var queryField = document.querySelector(".query");
	console.log("queryfield found");
	if (historyField == null || queryField == null) {
		return;
	}
	var start = historyField.selectionStart;
	var end = historyField.selectionEnd;

	var selection = null;
	if (start >= 0 && end >= 0) {
		selection = historyField.value.substring(start, end);
		queryField.value = selection;
		console.log("value set to " + selection);
	}
}

function setBrowseSql(sel) {
	var selectedTable = sel.value;
	var query = document.querySelector(".query");
	query.value = "SELECT * FROM " + selectedTable + ";";

	var runquery = document.querySelector(".runquery");
	runquery.click();
}

function insertIntoTextarea(thOrTd) {
    //tablecellelement
	
	var nodeName = thOrTd.nodeName;  //TH or TD
	var newText = thOrTd.innerText;
	console.log(thOrTd + ":" + nodeName + ":" + newText);
	if( newText == null || newText==undefined) {
		console.dir(thOrTd);
		newText=thOrTd+"";
	}
	if( newText == null || newText==undefined) {
		return;
	}
	
	newText = newText.trim();
	if( nodeName == "TD") {
		newText = "'" +newText+"'";
	}
	var el = document.querySelector(".query");
	var start = el.selectionStart;
	var end = el.selectionEnd;
	var text = el.value;
	
	var before = text.substring(0, start);
	var after = text.substring(end, text.length);
	
	el.value = (before + newText + after);
	el.selectionStart = el.selectionEnd = start + newText.length;
	el.focus();
}

function displayDropdown(e) {
	var x = (window.Event) ? e.pageX
			: event.clientX
					+ (document.documentElement.scrollLeft ? document.documentElement.scrollLeft
							: document.body.scrollLeft);
	var y = (window.Event) ? e.pageY
			: event.clientY
					+ (document.documentElement.scrollTop ? document.documentElement.scrollTop
							: document.body.scrollTop);
	
    //alert("x=" + x + " y=" + y);
	//alert(dropdown);
	
	
	document.querySelector("input[id$=\"dropdownx\"]").value=x;
	document.querySelector("input[id$=\"dropdowny\"]").value=y;

}
