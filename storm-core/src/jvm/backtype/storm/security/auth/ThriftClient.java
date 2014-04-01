/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package backtype.storm.security.auth;

import java.io.IOException;
import java.util.Map;
import javax.security.auth.login.Configuration;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import backtype.storm.utils.Utils;
import backtype.storm.Config;
import backtype.storm.security.auth.TBackoffConnect;

public class ThriftClient {	
    private static final Logger LOG = LoggerFactory.getLogger(ThriftClient.class);
    private TTransport _transport;
    protected TProtocol _protocol;
    private String _host;
    private int _port;
    private Integer _timeout;
    private ITransportPlugin _transportPlugin;
    private Map _conf;

    public ThriftClient(Map storm_conf, String host, int port) {
        this(storm_conf, host, port, null);
    }

    public ThriftClient(Map storm_conf, String host, int port, Integer timeout) {
        this._host = host;
        this._port = port;
        this._timeout = timeout;
        this._conf = storm_conf;
        //locate login configuration 
        Configuration login_conf = AuthUtils.GetConfiguration(storm_conf);

        //construct a transport plugin
        this._transportPlugin = AuthUtils.GetTransportPlugin(storm_conf, login_conf, null);

        //create a socket with server
        if (host==null) {
            throw new IllegalArgumentException("host is not set");
        }
        if (port<=0) {
            throw new IllegalArgumentException("invalid port: "+port);
        }
        reconnect();
    }

    public synchronized TTransport transport() {
        return _transport;
    }
    
    public synchronized void reconnect() {
        close();    
        try {
            TSocket socket = new TSocket(_host, _port);
            if(_timeout!=null) {
                socket.setTimeout(_timeout);
            }
            final TTransport underlyingTransport = socket;

            //establish client-server transport via plugin
            //do retries if the connect fails
            TBackoffConnect connectionRetry 
                = new TBackoffConnect(
                                      Utils.getInt(_conf.get(Config.STORM_NIMBUS_RETRY_TIMES)),
                                      Utils.getInt(_conf.get(Config.STORM_NIMBUS_RETRY_INTERVAL)),
                                      Utils.getInt(_conf.get(Config.STORM_NIMBUS_RETRY_INTERVAL_CEILING)));
            _transport = connectionRetry.doConnectWithRetry(_transportPlugin, underlyingTransport, _host);

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        _protocol = null;
        if (_transport != null) {
            _protocol = new  TBinaryProtocol(_transport);
        }
    }

    public synchronized void close() {
        if (_transport != null) {
            _transport.close();
            _transport = null;
            _protocol = null;
        }
    }
}
