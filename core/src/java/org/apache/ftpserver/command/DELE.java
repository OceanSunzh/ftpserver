/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */  

package org.apache.ftpserver.command;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.ftpserver.FtpSessionImpl;
import org.apache.ftpserver.ftplet.FileObject;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpReply;
import org.apache.ftpserver.ftplet.FtpReplyOutput;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.ftplet.FtpletEnum;
import org.apache.ftpserver.interfaces.FtpServerContext;
import org.apache.ftpserver.interfaces.ServerFtpStatistics;
import org.apache.ftpserver.listener.Connection;
import org.apache.ftpserver.util.FtpReplyUtil;

/**
 * <code>DELE &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
 *
 * This command causes the file specified in the pathname to be
 * deleted at the server site.
 */
public 
class DELE extends AbstractCommand {
    

    /**
     * Execute command.
     */
    public void execute(Connection connection,
                        FtpRequest request, 
                        FtpSessionImpl session, 
                        FtpReplyOutput out) throws IOException, FtpException {
        
        // reset state variables
        session.resetState(); 
        FtpServerContext serverContext = connection.getServerContext();
        
        // argument check
        String fileName = request.getArgument();
        if(fileName == null) {
            out.write(FtpReplyUtil.translate(session, FtpReply.REPLY_501_SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS, "DELE", null));
            return;  
        }
        
        // call Ftplet.onDeleteStart() method
        Ftplet ftpletContainer = serverContext.getFtpletContainer();
        FtpletEnum ftpletRet;
        try {
            ftpletRet = ftpletContainer.onDeleteStart(session, request, out);
        } catch(Exception e) {
            log.debug("Ftplet container threw exception", e);
            ftpletRet = FtpletEnum.RET_DISCONNECT;
        }
        if(ftpletRet == FtpletEnum.RET_SKIP) {
            out.write(FtpReplyUtil.translate(session, FtpReply.REPLY_450_REQUESTED_FILE_ACTION_NOT_TAKEN, "DELE", fileName));
            return;
        }
        else if(ftpletRet == FtpletEnum.RET_DISCONNECT) {
            serverContext.getConnectionManager().closeConnection(connection);
            return;
        }

        // get file object
        FileObject file = null;
        
        try {
            file = session.getFileSystemView().getFileObject(fileName);
        }
        catch(Exception ex) {
            log.debug("Could not get file " + fileName, ex);
        }
        if(file == null) {
            out.write(FtpReplyUtil.translate(session, FtpReply.REPLY_550_REQUESTED_ACTION_NOT_TAKEN, "DELE.invalid", fileName));
            return;
        }

        // check file
        fileName = file.getFullName();

        if( !file.hasDeletePermission() ) {
            out.write(FtpReplyUtil.translate(session, FtpReply.REPLY_450_REQUESTED_FILE_ACTION_NOT_TAKEN, "DELE.permission", fileName));
            return;
        }
        
        // now delete
        if(file.delete()) {
            out.write(FtpReplyUtil.translate(session, FtpReply.REPLY_250_REQUESTED_FILE_ACTION_OKAY, "DELE", fileName)); 
            
            // log message
            String userName = session.getUser().getName();
            Log log = serverContext.getLogFactory().getInstance(getClass());
            log.info("File delete : " + userName + " - " + fileName);
            
            // notify statistics object
            ServerFtpStatistics ftpStat = (ServerFtpStatistics)serverContext.getFtpStatistics();
            ftpStat.setDelete(connection, file);
            
            // call Ftplet.onDeleteEnd() method
            try{
                ftpletRet = ftpletContainer.onDeleteEnd(session, request, out);
            } catch(Exception e) {
                log.debug("Ftplet container threw exception", e);
                ftpletRet = FtpletEnum.RET_DISCONNECT;
            }
            if(ftpletRet == FtpletEnum.RET_DISCONNECT) {
                serverContext.getConnectionManager().closeConnection(connection);
                return;
            }

        }
        else {
            out.write(FtpReplyUtil.translate(session, FtpReply.REPLY_450_REQUESTED_FILE_ACTION_NOT_TAKEN, "DELE", fileName));
        }
    }

}
