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

package org.restlet.example.book.rest.ch3;

import java.util.ArrayList;
import java.util.List;

import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.DomRepresentation;
import org.w3c.dom.Node;

/**
 * Amazon S3 bucket.
 * 
 * @author Jerome Louvel (contact@noelios.com)
 */
public class S3Bucket extends S3Authorized {

    private String name;

    private boolean truncated;

    public S3Bucket(String name) {
        this.name = name;
    }

    /**
     * Deletes this bucket.
     */
    public Status delete() {
        return authorizedDelete(getUri()).getStatus();
    }

    public String getName() {
        return this.name;
    }

    /**
     * Get the objects in this bucket: all of them, or some subset.
     * 
     * If S3 decides not to return the whole bucket/subset, the second return
     * value will be set to true. To get the rest of the objects, you'll need to
     * manipulate the subset options.
     * 
     * Subset options are :Prefix, :Marker, :Delimiter, :MaxKeys. For details,
     * see the S3 docs on "Listing Keys".
     * 
     * @return The objects in this nucket.
     */
    public List<S3Object> getObjects(String prefix, String marker,
            String delimiter, Integer maxKeys) {
        final List<S3Object> result = new ArrayList<S3Object>();

        // Construct the request URI by appending optional listing keys
        final StringBuilder uri = new StringBuilder().append(getUri());
        String suffix = "?";
        if (prefix != null) {
            uri.append(suffix).append("prefix=").append(prefix);
            suffix = "&";
        }
        if (marker != null) {
            uri.append(suffix).append("marker=").append(marker);
            suffix = "&";
        }
        if (delimiter != null) {
            uri.append(suffix).append("delimiter=").append(delimiter);
            suffix = "&";
        }
        if (maxKeys != null) {
            uri.append(suffix).append("maxKeys=").append(maxKeys);
            suffix = "&";
        }

        // Make the request and parse the document.
        final Response response = authorizedGet(uri.toString());
        final DomRepresentation document = response.getEntityAsDom();

        // Update the truncated flag
        this.truncated = document.getNodes("//IsTruncated").get(0)
                .getTextContent().equals("true");

        // Browse the list of object keys
        for (final Node node : document.getNodes("//Contents/Key")) {
            result.add(new S3Object(this, node.getTextContent()));
        }

        return result;
    }

    public String getUri() {
        return HOST + getName();
    }

    public boolean isTruncated() {
        return this.truncated;
    }

    /**
     * Stores this bucket on S3. Analagous to ActiveRecord::Base#save, which
     * stores an object in the database.
     */
    public Status save() {
        return authorizedPut(getUri(), null).getStatus();
    }

    public void setName(String name) {
        this.name = name;
    }

}
