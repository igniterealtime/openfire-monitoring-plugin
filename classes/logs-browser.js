(function() {
  "use strict";

  function toInt(value) {
    var parsed = parseInt(value, 10);
    return isNaN(parsed) ? null : parsed;
  }

  function updateUrl(canonicalPath, selectedIds) {
    if (!canonicalPath) {
      return;
    }

    var url = new URL(window.location.href);
    url.pathname = canonicalPath;
    url.search = "";

    if (selectedIds.length === 1) {
      url.searchParams.set("id", String(selectedIds[0]));
    } else if (selectedIds.length > 1) {
      url.searchParams.set("from", String(selectedIds[0]));
      url.searchParams.set("to", String(selectedIds[selectedIds.length - 1]));
    }

    window.history.replaceState({}, "", url.toString());
  }

  function applySelection(rows, startId, endId) {
    var low = Math.min(startId, endId);
    var high = Math.max(startId, endId);
    var selected = [];

    rows.forEach(function(row) {
      var id = toInt(row.dataset.messageId);
      var on = id !== null && id >= low && id <= high;
      row.classList.toggle("selected", on);
      if (on) {
        selected.push(id);
      }
    });

    selected.sort(function(a, b) {
      return a - b;
    });
    return selected;
  }

  document.addEventListener("DOMContentLoaded", function() {
    var rows = Array.prototype.slice.call(document.querySelectorAll("#message-table tbody tr.message-row"));
    if (rows.length === 0) {
      return;
    }

    var anchorId = null;
    rows.forEach(function(row) {
      if (row.classList.contains("selected") && anchorId === null) {
        anchorId = toInt(row.dataset.messageId);
      }
    });

    // For clean URLs without hashes, scroll to the first query-selected row on initial load.
    if (anchorId !== null) {
      var initialRow = document.getElementById("m-" + anchorId);
      if (initialRow) {
        initialRow.scrollIntoView({ block: "center" });
      }
    }

    rows.forEach(function(row) {
      row.addEventListener("mousedown", function(event) {
        // Avoid native text-selection behavior when selecting rows, especially with SHIFT.
        event.preventDefault();
      });

      row.addEventListener("click", function(event) {
        event.preventDefault();

        var clickedId = toInt(row.dataset.messageId);
        if (clickedId === null) {
          return;
        }

        var start = clickedId;
        if (event.shiftKey && anchorId !== null) {
          start = anchorId;
        }

        var selectedIds = applySelection(rows, start, clickedId);
        anchorId = clickedId;

        var canonicalPath = row.dataset.canonicalPath;
        updateUrl(canonicalPath, selectedIds);
      });
    });
  });
})();

