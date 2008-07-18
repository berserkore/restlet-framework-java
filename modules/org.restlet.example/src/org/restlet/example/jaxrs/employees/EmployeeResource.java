/*
 * Copyright 2005-2008 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */
package org.restlet.example.jaxrs.employees;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;
import javax.ws.rs.core.UriInfo;

/**
 * This resource class represents one employee.
 * 
 * @author Stephan Koops
 * @see EmployeesResource
 */
public class EmployeeResource {

    /**
     * @param uriInfo
     * @return
     * @throws IllegalArgumentException
     * @throws UriBuilderException
     */
    private static URI createEmployeesUri(final UriInfo uriInfo) {
        final UriBuilder employeesUri = uriInfo.getBaseUriBuilder();
        employeesUri.path(uriInfo.getAncestorResourceURIs().get(0));
        employeesUri.extension(uriInfo.getConnegExtension());
        final URI build = employeesUri.build();
        return build;
    }

    private final EmployeeMgr employeeMgr = EmployeeMgr.get();

    private final int staffNo;

    EmployeeResource(int persNr) {
        this.staffNo = persNr;
    }

    @DELETE
    public Object delete(@Context HttpHeaders httpHeaders,
            @Context UriInfo uriInfo) {
        this.employeeMgr.remove(this.staffNo);
        if (httpHeaders.getAcceptableMediaTypes().contains(
                MediaType.TEXT_HTML_TYPE)) {
            return Response.seeOther(createEmployeesUri(uriInfo));
        }
        return null;
    }

    @GET
    @Consumes( { "application/xml", "text/xml", "application/json" })
    public Employee get(@Context UriInfo uriInfo) {
        // load employee with requested id
        final Employee employee = this.employeeMgr.getFull(this.staffNo);

        // set department uri
        final UriBuilder departmentUB = uriInfo.getBaseUriBuilder();
        departmentUB.path("departments", "{depId}");
        // LATER departmentUB.extension(uriInfo.getPathExtension());
        final String department = employee.getDepartment();
        employee.setDepartmentUri(departmentUB.build(department));

        return employee;
    }

    @GET
    @Produces("text/html")
    public StreamingOutput getHtml(@Context final UriInfo uriInfo) {
        final Employee employee = get(uriInfo);
        final URI employeesUri = createEmployeesUri(uriInfo);
        return new StreamingOutput() {
            public void write(OutputStream output) throws IOException {
                final PrintStream ps = new PrintStream(output);
                ps.println("<html><head>");
                ps.println("<title>Employee</title>");
                ps.println("</head></body>");
                ps.println("<h2>Employee</h2>");
                ps.println("<table><tr>");
                ps.println("<td>staff no.</td>");
                ps.println("<td>");
                ps.println(employee.getStaffNo());
                ps.println("</td>");
                ps.println("</tr><tr>");
                ps.println("<td>firstname:</td>");
                ps.println("<td>");
                ps.println(employee.getFirstname());
                ps.println("</td>");
                ps.println("</tr><tr>");
                ps.println("<td>lastname:</td>");
                ps.println("<td>");
                ps.println(employee.getLastname());
                ps.println("</td>");
                ps.println("</tr><tr>");
                ps.println("<td>sex:</td>");
                ps.println("<td>");
                ps.println(employee.getSex());
                ps.println("</td>");
                ps.println("</tr><tr>");
                ps.println("<td>department:</td>");
                ps.println("<td>");
                ps.println(employee.getDepartment());
                ps.println("</td>");
                // departments are not implemented.
                ps.println("</tr></table>");
                ps.println("<hr>");
                ps.print("<form action=\"");
                ps.print(uriInfo.getAbsolutePath());
                ps.println("?method=DELETE\" method=\"POST\">");
                ps
                        .println("<input type=\"submit\" value=\"Delete employee\" />");
                ps.println("</form>");
                ps.print("<hr><a href=\"");
                ps.print(employeesUri);
                ps.print("\">all employees</a>");
                ps.println("</body></html>");
            }
        };
    }

    @PUT
    @Consumes( { "application/xml", "text/xml", "application/json" })
    public void update(Employee employee) {
        this.employeeMgr.update(this.staffNo, employee);
    }
}