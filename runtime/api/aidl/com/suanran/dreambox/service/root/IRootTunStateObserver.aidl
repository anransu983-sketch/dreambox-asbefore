package com.suanran.dreambox.service.root;

interface IRootTunStateObserver {
    oneway void onStatusChanged(String statusJson);
}
