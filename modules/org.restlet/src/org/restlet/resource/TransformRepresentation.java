package org.restlet.resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.restlet.Context;
import org.restlet.data.Reference;
import org.restlet.data.Response;
import org.xml.sax.InputSource;
import org.xml.sax.XMLFilter;

/**
 * Representation able to apply an XSLT transformation. The internal JAXP
 * transformer is created when the getTransformer() method is first called. So,
 * if you need to specify a custom URI resolver, you need to do it before
 * actually using the representation for a transformation.<br>
 * <br>
 * This representation should be viewed as a wrapper representation that applies
 * a transform sheet on a source representation when it is read or written out.
 * Therefore, it isn't intended to be reused on different sources. For this use
 * case, you should instead use the {@link org.restlet.Transformer} filter.
 * 
 * @author Jerome Louvel (contact@noelios.com) <a
 *         href="http://www.noelios.com/">Noelios Consulting</a>
 */
public class TransformRepresentation extends OutputRepresentation {
    /**
     * URI resolver based on a Restlet Context instance.
     * 
     * @author Jerome Louvel (contact@noelios.com)
     */
    private final static class ContextResolver implements URIResolver {
        /** The Restlet context. */
        private final Context context;

        /**
         * Constructor.
         * 
         * @param context
         *            The Restlet context.
         */
        public ContextResolver(Context context) {
            this.context = context;
        }

        /**
         * Resolves a target reference into a Source document.
         * 
         * @see javax.xml.transform.URIResolver#resolve(java.lang.String,
         *      java.lang.String)
         */
        public Source resolve(String href, String base)
                throws TransformerException {
            Source result = null;

            if (this.context != null) {
                Reference targetRef = null;

                if ((base != null) && !base.equals("")) {
                    // Potentially a relative reference
                    final Reference baseRef = new Reference(base);
                    targetRef = new Reference(baseRef, href);
                } else {
                    // No base, assume "href" is an absolute URI
                    targetRef = new Reference(href);
                }

                final String targetUri = targetRef.getTargetRef().toString();
                final Response response = this.context.getClientDispatcher()
                        .get(targetUri);
                if (response.getStatus().isSuccess()
                        && response.isEntityAvailable()) {
                    try {
                        result = new StreamSource(response.getEntity()
                                .getStream());
                        result.setSystemId(targetUri);

                    } catch (final IOException e) {
                        this.context.getLogger().log(Level.WARNING,
                                "I/O error while getting the response stream",
                                e);
                    }
                }
            }

            return result;
        }
    }

    /** The JAXP transformer output properties. */
    private volatile Map<String, String> outputProperties;

    /** The JAXP transformer parameters. */
    private volatile Map<String, Object> parameters;

    /** The source representation to transform. */
    private volatile Representation sourceRepresentation;

    /** The template to be used and reused. */
    private volatile Templates templates;

    /** The XSLT transform sheet to apply to message entities. */
    private volatile Representation transformSheet;

    /** The URI resolver. */
    private volatile URIResolver uriResolver;

    /**
     * Constructor. Note that a default URI resolver will be created based on
     * the given context.
     * 
     * @param context
     *            The parent context.
     * @param source
     *            The source representation to transform.
     * @param transformSheet
     *            The XSLT transform sheet to apply.
     */
    public TransformRepresentation(Context context, Representation source,
            Representation transformSheet) {
        this((context == null) ? null : new ContextResolver(context), source,
                transformSheet);
    }

    /**
     * Default constructor.
     * 
     * @param source
     *            The source representation to transform.
     * @param transformSheet
     *            The XSLT transform sheet to apply.
     */
    public TransformRepresentation(Representation source,
            Representation transformSheet) {
        this((URIResolver) null, source, transformSheet);
    }

    /**
     * Constructor. Note that a default URI resolver will be created based on
     * the given context.
     * 
     * @param uriResolver
     *            The JAXP URI resolver.
     * @param source
     *            The source representation to transform.
     * @param transformSheet
     *            The XSLT transform sheet to apply.
     */
    public TransformRepresentation(URIResolver uriResolver,
            Representation source, Representation transformSheet) {
        this(uriResolver, source, transformSheet, null);
    }

    /**
     * Constructor.
     * 
     * @param uriResolver
     *            The optional JAXP URI resolver.
     * @param source
     *            The source representation to transform.
     * @param templates
     *            The precompiled JAXP template.
     */
    private TransformRepresentation(URIResolver uriResolver,
            Representation source, Representation transformSheet,
            Templates templates) {
        super(null);
        this.sourceRepresentation = source;
        this.templates = templates;
        this.transformSheet = transformSheet;
        this.uriResolver = uriResolver;
        this.parameters = new HashMap<String, Object>();
        this.outputProperties = new HashMap<String, String>();
    }

    /**
     * Constructor.
     * 
     * @param uriResolver
     *            The optional JAXP URI resolver.
     * @param source
     *            The source representation to transform.
     * @param templates
     *            The precompiled JAXP template.
     */
    public TransformRepresentation(URIResolver uriResolver,
            Representation source, Templates templates) {
        this(uriResolver, source, null, templates);
    }

    /**
     * Returns the modifiable map of JAXP transformer output properties.
     * 
     * @return The JAXP transformer output properties.
     */
    public Map<String, String> getOutputProperties() {
        return this.outputProperties;
    }

    /**
     * Returns the modiable map of JAXP transformer parameters.
     * 
     * @return The JAXP transformer parameters.
     */
    public Map<String, Object> getParameters() {
        return this.parameters;
    }

    /**
     * Returns the SAX source associated to the source representation.
     * 
     * @return The SAX source associated to the source representation.
     * @throws IOException
     */
    public SAXSource getSaxSource() throws IOException {
        SAXSource source = null;

        if (getSourceRepresentation() instanceof XmlRepresentation) {
            source = ((XmlRepresentation) getSourceRepresentation())
                    .getSaxSource();
        } else if (getSourceRepresentation() instanceof TransformRepresentation) {
            final TransformRepresentation lastTR = (TransformRepresentation) getSourceRepresentation();
            TransformRepresentation rootTR = lastTR;
            final XMLFilter lastFilter = lastTR.getXmlFilter();
            XMLFilter rootFilter = lastFilter;
            XMLFilter currFilter = null;

            // Walk up the transformation hierarchy while
            // building the chain of SAX filters
            while (rootTR.getSourceRepresentation() instanceof TransformRepresentation) {
                rootTR = (TransformRepresentation) rootTR
                        .getSourceRepresentation();
                currFilter = rootTR.getXmlFilter();
                rootFilter.setParent(currFilter);
                rootFilter = currFilter;
            }

            InputSource rootSource = null;
            if (rootTR.getSourceRepresentation() instanceof XmlRepresentation) {
                rootSource = ((XmlRepresentation) rootTR
                        .getSourceRepresentation()).getSaxSource()
                        .getInputSource();
            } else {
                rootSource = new InputSource(rootTR.getSourceRepresentation()
                        .getStream());
            }

            source = new SAXSource(lastFilter, rootSource);
        } else {
            // Prepare the source and result documents
            source = new SAXSource(new InputSource(getSourceRepresentation()
                    .getStream()));
        }

        if (getSourceRepresentation().getIdentifier() != null) {
            source.setSystemId(getSourceRepresentation().getIdentifier()
                    .getTargetRef().toString());
        }

        return source;
    }

    /**
     * Returns the default SAX transformer factory.
     * 
     * @return The default SAX transformer factory.
     */
    private SAXTransformerFactory getSaxTransformerFactory() {
        final SAXTransformerFactory result = (SAXTransformerFactory) TransformerFactory
                .newInstance();
        return result;
    }

    /**
     * Returns the source representation to transform.
     * 
     * @return The source representation to transform.
     */
    public Representation getSourceRepresentation() {
        return this.sourceRepresentation;
    }

    /**
     * Returns the templates to be used and reused. If no one exists, it creates
     * a new one based on the transformSheet representation and on the URI
     * resolver.
     * 
     * @return The templates to be used and reused.
     */
    public Templates getTemplates() throws IOException {
        if (this.templates == null) {
            try {
                // Prepare the XSLT transformer documents
                final StreamSource transformSource = new StreamSource(
                        getTransformSheet().getStream());

                if (getTransformSheet().getIdentifier() != null) {
                    transformSource.setSystemId(getTransformSheet()
                            .getIdentifier().getTargetRef().toString());
                }

                // Create the transformer factory
                final TransformerFactory transformerFactory = TransformerFactory
                        .newInstance();

                // Set the URI resolver
                if (getUriResolver() != null) {
                    transformerFactory.setURIResolver(getUriResolver());
                }

                // Create a new transformer
                this.templates = transformerFactory
                        .newTemplates(transformSource);
            } catch (final TransformerConfigurationException tce) {
                throw new IOException("Transformer configuration exception. "
                        + tce.getMessage());
            }
        }

        return this.templates;
    }

    /**
     * Returns a new transformer to be used. Creation is based on the
     * {@link #getTemplates()}.newTransformer() method.
     * 
     * @return The new transformer to be used.
     */
    public Transformer getTransformer() throws IOException {
        Transformer result = null;

        try {
            final Templates templates = getTemplates();

            if (templates != null) {
                result = templates.newTransformer();

                if (this.uriResolver != null) {
                    result.setURIResolver(getUriResolver());
                }

                // Set the parameters
                for (final String name : getParameters().keySet()) {
                    result.setParameter(name, getParameters().get(name));
                }

                // Set the output properties
                for (final String name : getOutputProperties().keySet()) {
                    result.setOutputProperty(name, getOutputProperties().get(
                            name));
                }
            }
        } catch (final TransformerConfigurationException tce) {
            throw new IOException("Transformer configuration exception. "
                    + tce.getMessage());
        } catch (final TransformerFactoryConfigurationError tfce) {
            throw new IOException(
                    "Transformer factory configuration exception. "
                            + tfce.getMessage());
        }

        return result;
    }

    /**
     * Returns the SAX transformer handler associated to the transform sheet.
     * 
     * @return The SAX transformer handler.
     * @throws IOException
     */
    public TransformerHandler getTransformerHandler() throws IOException {
        TransformerHandler result = null;
        final Templates templates = getTemplates();

        if (templates != null) {
            try {
                result = getSaxTransformerFactory().newTransformerHandler(
                        templates);
            } catch (final TransformerConfigurationException tce) {
                throw new IOException("Transformer configuration exception. "
                        + tce.getMessage());
            }
        }

        return result;
    }

    /**
     * Returns the XSLT transform sheet to apply to the source representation.
     * 
     * @return The XSLT transform sheet to apply.
     */
    public Representation getTransformSheet() {
        return this.transformSheet;
    }

    /**
     * Returns the URI resolver.
     * 
     * @return The URI resolver.
     */
    public URIResolver getUriResolver() {
        return this.uriResolver;
    }

    /**
     * Returns the URI resolver.
     * 
     * @return The URI resolver.
     * @deprecated Use the getUriResolver method instead.
     */
    @Deprecated
    public URIResolver getURIResolver() {
        return this.uriResolver;
    }

    /**
     * Returns the SAX XML filter applying the transform sheet to its input.
     * 
     * @return The SAX XML filter.
     * @throws IOException
     */
    public XMLFilter getXmlFilter() throws IOException {
        XMLFilter result = null;
        final Templates templates = getTemplates();

        if (templates != null) {
            try {
                result = getSaxTransformerFactory().newXMLFilter(templates);
            } catch (final TransformerConfigurationException tce) {
                throw new IOException("Transformer configuration exception. "
                        + tce.getMessage());
            }
        }

        return result;
    }

    /**
     * Releases the source and transform sheet representations, the transformer
     * and the URI resolver.
     */
    @Override
    public void release() {
        if (this.sourceRepresentation != null) {
            this.sourceRepresentation.release();
            this.sourceRepresentation = null;
        }

        if (this.templates != null) {
            this.templates = null;
        }

        if (this.transformSheet != null) {
            this.transformSheet.release();
            this.transformSheet = null;
        }

        if (this.uriResolver != null) {
            this.uriResolver = null;
        }

        super.release();
    }

    /**
     * Sets the modifiable map of JAXP transformer output properties.
     * 
     * @param outputProperties
     *            The JAXP transformer output properties.
     */
    public void setOutputProperties(Map<String, String> outputProperties) {
        this.outputProperties = outputProperties;
    }

    /**
     * Sets the JAXP transformer parameters.
     * 
     * @param parameters
     *            The JAXP transformer parameters.
     */
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    /**
     * Sets the source representation to transform.
     * 
     * @param source
     *            The source representation to transform.
     */
    public void setSourceRepresentation(Representation source) {
        this.sourceRepresentation = source;
    }

    /**
     * Sets the templates to be used and reused.
     * 
     * @param templates
     *            The templates to be used and reused.
     */
    public void setTemplates(Templates templates) {
        this.templates = templates;
    }

    /**
     * Sets the XSLT transform sheet to apply to message entities.
     * 
     * @param transformSheet
     *            The XSLT transform sheet to apply to message entities.
     */
    public void setTransformSheet(Representation transformSheet) {
        this.transformSheet = transformSheet;
    }

    /**
     * Sets the URI resolver.
     * 
     * @param uriResolver
     *            The URI resolver.
     */
    public void setUriResolver(URIResolver uriResolver) {
        this.uriResolver = uriResolver;
    }

    @Override
    public void write(OutputStream outputStream) throws IOException {
        try {
            // Generates the result of the transformation
            getTransformer().transform(getSaxSource(),
                    new StreamResult(outputStream));
        } catch (final TransformerException te) {
            throw new IOException("Transformer exception. " + te.getMessage());
        }
    }
}
