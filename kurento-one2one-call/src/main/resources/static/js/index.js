/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */

var ws = new WebSocket('ws://' + location.host + '/call');
var videoInput;
var videoOutput;
var webRtcPeer;
var response;
var callerMessage;
var from;

var registerName = null;
var registerState = null;
const NOT_REGISTERED = 0;
const REGISTERING = 1;
const REGISTERED = 2;

function setRegisterState(nextState) {
	switch (nextState) {
	case NOT_REGISTERED:
		$('#register').attr('disabled', false);
		$('#terminate').attr('disabled', true);
		break;
	case REGISTERING:
		$('#register').attr('disabled', true);
		break;
	case REGISTERED:
		$('#register').attr('disabled', true);
		setCallState(NO_CALL);
		break;
	default:
		return;
	}
	registerState = nextState;
}

var callState = null;
const NO_CALL = 0;
const PROCESSING_CALL = 1;
const IN_CALL = 2;

function setCallState(nextState) {
	switch (nextState) {
	case NO_CALL:
		$('#terminate').attr('disabled', true);
		break;
	case PROCESSING_CALL:
		$('#terminate').attr('disabled', true);
		break;
	case IN_CALL:
		$('#terminate').attr('disabled', false);
		break;
	default:
		return;
	}
	callState = nextState;
}

window.onload = function() {
	console = new Console();
	setRegisterState(NOT_REGISTERED);
	var drag = new Draggabilly(document.getElementById('videoSmall'));
	videoInput = document.getElementById('videoInput');
	videoOutput = document.getElementById('videoOutput');
	document.getElementById('name').focus();
}

window.onbeforeunload = function() {
	ws.close();
}

ws.onmessage = function(message) {
	var parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

	switch (parsedMessage.id) {
	case 'registerResponse':
		registerResponse(parsedMessage);
		break;
	case 'startCommunication':
		startCommunication(parsedMessage);
		break;
	case 'stopCommunication':
		console.info('Communication ended by remote peer');
		stop(true);
		break;
	case 'iceCandidate':
		webRtcPeer.addIceCandidate(parsedMessage.candidate, function(error) {
			if (error)
				return console.error('Error adding candidate: ' + error);
		});
		break;
	case 'chatMessage':
		chatMessage(parsedMessage);
		break;
	default:
		console.error('Unrecognized message', parsedMessage);
	}
}

function registerResponse(message) {
	if (message.response == 'accepted') {
		setRegisterState(REGISTERED);
	} else {
		setRegisterState(NOT_REGISTERED);
		var errorMessage = message.message;
		console.log(errorMessage);
		alert('Error registering user. See console for further information.');
	}
}

function startCommunication(message) {
	setCallState(IN_CALL);
	webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
		if (error)
			return console.error(error);
	});
}

function chatMessage(message) {
	console.info(message.displayName + ' sent \'' + message.body + '\'');
}

function register() {
	var name = document.getElementById('name').value;
	if (name == '') {
		window.alert('You must insert your user name');
		return;
	}
	setRegisterState(REGISTERING);
	generateOffer();
}

function generateOffer() {
	setCallState(PROCESSING_CALL);
	showSpinner(videoInput, videoOutput);

	var options = {
		localVideo : videoInput,
		remoteVideo : videoOutput,
		onicecandidate : onIceCandidate,
		onerror : onError
	}
	webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
			function(error) {
				if (error) {
					return console.error(error);
				}
				webRtcPeer.generateOffer(onOfferCall);
			});
}

function onOfferCall(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.log('Invoking SDP offer callback function');

	var name = document.getElementById('name').value;
	var room = document.getElementById('room').value;
	var message = {
		id : 'register',
		name : name,
		room : room,
		sdpOffer : offerSdp
	};
	sendMessage(message);
}

function stop(message) {
	setCallState(NO_CALL);
	if (webRtcPeer) {
		webRtcPeer.dispose();
		webRtcPeer = null;

		if (!message) {
			var message = {
				id : 'stop'
			}
			sendMessage(message);
		}
	}
	hideSpinner(videoInput, videoOutput);
}

function onError() {
	setCallState(NO_CALL);
}

function onIceCandidate(candidate) {
	console.log("Local candidate" + JSON.stringify(candidate));

	var message = {
		id : 'onIceCandidate',
		candidate : candidate
	};
	sendMessage(message);
}

function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	console.log('Senging message: ' + jsonMessage);
	ws.send(jsonMessage);
}

function showSpinner() {
	for (var i = 0; i < arguments.length; i++) {
		arguments[i].poster = './img/transparent-1px.png';
		arguments[i].style.background = 'center transparent url("./img/spinner.gif") no-repeat';
	}
}

function hideSpinner() {
	for (var i = 0; i < arguments.length; i++) {
		arguments[i].src = '';
		arguments[i].poster = './img/webrtc.png';
		arguments[i].style.background = '';
	}
}

/**
 * Lightbox utility (to display media pipeline image in a modal dialog)
 */
$(document).delegate('*[data-toggle="lightbox"]', 'click', function(event) {
	event.preventDefault();
	$(this).ekkoLightbox();
});
