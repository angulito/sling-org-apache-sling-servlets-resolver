/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.scripting.bundle.tracker.internal;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

import javax.script.ScriptException;
import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.jetbrains.annotations.NotNull;

class BundledScriptServlet extends GenericServlet {


    private final ScriptContextProvider scriptContextProvider;
    private final LinkedHashSet<TypeProvider> wiredTypeProviders;
    private final Executable executable;


    BundledScriptServlet(@NotNull ScriptContextProvider scriptContextProvider,
                         @NotNull LinkedHashSet<TypeProvider> wiredTypeProviders,
                         @NotNull Executable executable) {
        this.scriptContextProvider = scriptContextProvider;
        this.wiredTypeProviders = wiredTypeProviders;
        this.executable = executable;
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        if ((req instanceof SlingHttpServletRequest) && (res instanceof SlingHttpServletResponse)) {
            SlingHttpServletRequest request = (SlingHttpServletRequest) req;
            SlingHttpServletResponse response = (SlingHttpServletResponse) res;

            if (request.getAttribute(SlingConstants.ATTR_INCLUDE_SERVLET_PATH) == null) {
                final String contentType = request.getResponseContentType();
                if (contentType != null) {
                    response.setContentType(contentType);
                    if (contentType.startsWith("text/")) {
                        response.setCharacterEncoding("UTF-8");
                    }
                }
            }

            RequestWrapper requestWrapper = new RequestWrapper(request,
                    wiredTypeProviders.stream().map(typeProvider -> typeProvider.getServletCapability().getResourceTypes()
            ).flatMap(Collection::stream).collect(Collectors.toSet()));
            ScriptContextProvider.ExecutableContext executableContext = scriptContextProvider
                    .prepareScriptContext(requestWrapper, response, executable);
            try {
                executableContext.eval();
            } catch (ScriptException se) {
                Throwable cause = (se.getCause() == null) ? se : se.getCause();
                throw new ServletException(String.format("Failed executing script %s: %s", executable.getName(), se.getMessage()), cause);
            } finally {
                executableContext.clean();
            }
        } else {
            throw new ServletException("Not a Sling HTTP request/response");
        }
    }
}
