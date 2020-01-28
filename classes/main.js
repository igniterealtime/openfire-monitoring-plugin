const ROOT = "api/logs/conference/";

let roomIds;
let dates;
let logs;

getJson(ROOT).then(function(json) {
  roomIds = json;
  initializeRoomSelector();
});

function getJson(url) {
  return fetch(url)
    .then(function(response) {
      return response.json();
    })
    .catch(function(err) {
      //TODO handle error
      console.log("Fetch problem: " + err.message);
    });
}

function initializeRoomSelector() {
  const room = document.querySelector("#room");
  room.addEventListener("change", selectRoom);
  for (var i = 0; i < roomIds.length; i++) {
    var option = document.createElement("option");
    option.value = roomIds[i];
    option.text = roomIds[i];
    room.appendChild(option);
  }
}

function initializeDateSelector() {
  const dateSelect = document.querySelector("#date");
  const dateInput = document.createElement("input");
  const dateLabel = document.querySelector("#dateLabel");
  dateInput.type = "date";
  dateInput.addEventListener("change", selectDate);
  dateSelect.appendChild(dateInput);
  dateLabel.style.display = "flex";
}

function initializeMainTable() {
  const logsView = document.querySelector("#logs");
  clearLogsView();
  const thead = document.createElement("thead");
  const row = document.createElement("tr");
  row.appendChild(createTh("Time (UTC)"));
  row.appendChild(createTh("Nickname"));
  row.appendChild(createTh("Message"));
  thead.appendChild(row);
  logsView.appendChild(thead);

  const tbody = document.createElement("tbody");
  for (var i = 0; i < logs.length; i++) {
    const timestampDate = new Date(logs[i].timestamp);
    const timeString =
      ("0" + timestampDate.getUTCHours()).slice(-2) +
      ":" +
      ("0" + timestampDate.getUTCMinutes()).slice(-2) +
      ":" +
      ("0" + timestampDate.getUTCSeconds()).slice(-2);

    let dataRow = document.createElement("tr");
    dataRow.appendChild(createTd(timeString));
    dataRow.appendChild(createTd(logs[i].nickname));
    dataRow.appendChild(createTd(logs[i].message));
    tbody.appendChild(dataRow);
  }
  logsView.appendChild(tbody);
}

function createTh(text) {
  const th = document.createElement("th");
  const span = document.createElement("span");
  span.className = "text";
  span.appendChild(document.createTextNode(text));
  th.appendChild(span);
  return th;
}

function createTd(text) {
  const td = document.createElement("td");
  td.vAlign = "top";
  td.appendChild(document.createTextNode(text));
  return td;
}

function clearDateSelect() {
  const dateSelect = document.querySelector("#date");
  const dateLabel = document.querySelector("#dateLabel");
  dateLabel.style.display = "none";
  while (dateSelect.firstChild) {
    dateSelect.removeChild(dateSelect.firstChild);
  }
}

function clearLogsView() {
  const logsView = document.querySelector("#logs");
  while (logsView.firstChild) {
    logsView.removeChild(logsView.firstChild);
  }
}

function selectRoom() {
  const room = document.querySelector("#room");
  clearDateSelect();
  clearLogsView();
  if (room.value.length > 0) {
    getJson(ROOT + room.value).then(function(json) {
      dates = json;
      initializeDateSelector();
    });
  }
}

function selectDate() {
  const dateSelect = document.querySelector("#date").firstChild;
  getJson(ROOT + room.value + "/" + dateSelect.value).then(function(json) {
    logs = json;
    initializeMainTable();
  });
}
