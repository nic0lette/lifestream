<?php

/*
    LifeStream - Instant Photo Sharing
    Copyright (C) 2014 Kayateia

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
