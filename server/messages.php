<?php

$download_msgs = array(
	"Somebody loves you! <|",
	"Wee yea ra hymme an yanyaue yor!",
	"Sure beats Canada Post.",
	"Santa came early."
);

function get_download_msg() {
	global $download_msgs;
	return $download_msgs[rand(0, count($download_msgs)-1)];
}

$upload_msgs = array(
	"Accrroad briyante!",
	"Somebody's gonna be happy!",
	"Oh internet, accept this offering...",
	"Keep calm and carry bytes."
);

function get_upload_msg() {
	global $upload_msgs;
	return $upload_msgs[rand(0, count($upload_msgs)-1)];
}


?>
