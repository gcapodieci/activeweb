/*
Copyright 2009-2010 Igor Polevoy 

Licensed under the Apache License, Version 2.0 (the "License"); 
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at 

http://www.apache.org/licenses/LICENSE-2.0 

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and 
limitations under the License. 
*/
package org.javalite.activeweb;

import org.javalite.activeweb.annotations.RESTful;
import com.google.inject.Inject;
import org.javalite.common.Util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;


/**
 * Subclass this class to create application controllers. A controller is a main component of a web
 * application. Its main purpose in life is to process web requests. 
 *
 * @author Igor Polevoy
 */
public abstract class AppController extends HttpSupport {
    
    
    private HashMap<String, Object> values = new HashMap<String, Object>();

    /**
     * Assigns value that will be passed into view.
     * 
     * @param name name of a value.
     * @param value value.
     */
    protected void assign(String name, Object value) {

        if(value == null)
            throw new IllegalArgumentException("value '" + name + "' is null");
        
        KeyWords.check(name);
        values.put(name, value);
    }

    /**
     * Alias to {@link #assign(String, Object)}.
     *
     * @param name name of object to be passed to view
     * @param value object to be passed to view
     */
    protected void view(String name, Object value) {
        assign(name, value);
    }
    

    protected Map values() {
        return values;
    }

    /**
     * Renders results with a template.
     * This method is called from within an action execution.
     *
     * This call must be the last call in the action. All subsequent calls to assign values, render or respond will generate
     * {@link IllegalStateException}.
     *
     * @param template - template name, can be "list"  - for a view whose name is different than the name of this action, or
     *             "/another_controller/any_view" - this is a reference to a view from another controller. The format of this
     * parameter should be either a single word or two words separated by slash: '/'. If this is a single word, than
     * it is assumed that template belongs to current controller, if there is a slash used as a separator, then the
     * first word is assumed to be a name of another controller.
     * @return instance of {@link RenderBuilder}, which is used to provide additional parameters.
     */
    protected RenderBuilder render(String template) {

        String targetTemplate = template.startsWith("/")? template: Router.getControllerPath(getClass())
                + "/" + template;

        return render(targetTemplate, values());
    }

    /**
     * Use this method in order to override a layout, status code, and content type.
     *
     * @return instance of {@link RenderBuilder}, which is used to provide additional parameters.
     */
    protected RenderBuilder render(){

        String template = Router.getControllerPath(getClass()) + "/" + ContextAccess.getActionName();
        return super.render(template, values());
    }


    protected String servletPath() {
        return ContextAccess.getHttpRequest().getServletPath();
    }

    protected String queryString() {
        return ContextAccess.getHttpRequest().getQueryString();
    }

    protected InputStream getRequestInputStream() throws IOException {
        return ContextAccess.getHttpRequest().getInputStream();
    }

    /**
     * Alias to {@link #getRequestInputStream()}.
     * @return input stream to read data sent by client.
     * @throws IOException
     */
    protected InputStream getRequestStream() throws IOException {
        return ContextAccess.getHttpRequest().getInputStream();
    }

    /**
     * Reads entire request data as String. Do not use for large data sets to avoid
     * memory issues, instead use {@link #getRequestInputStream()}.
     *
     * @return data sent by client as string.
     * @throws IOException
     */
    protected String getRequestString() throws IOException {
        return Util.read(ContextAccess.getHttpRequest().getInputStream());
    }

    /**
     * Reads entire request data as byte array. Do not use for large data sets to avoid
     * memory issues.
     *
     * @return data sent by client as string.
     * @throws IOException
     */
    protected byte[] getRequestBytes() throws IOException {        
        return Util.bytes(ContextAccess.getHttpRequest().getInputStream());
    }

    protected void flash(String name, Object value) {
        if (session().get("flasher") == null) {
            session().put("flasher", new HashMap());
        }
        ((Map) session().get("flasher")).put(name, value);
    }

    /**
     * Returns a name for a default layout as provided in  <code>activeweb_defaults.properties</code> file.
     * Override this  method in a sub-class. Value expected is a fully qualified name of a layout template.
     * Example: <code>"/custom/custom_layout"</code>
     *
     * @return name of a layout for this controller and descendants if they do not override this method.
     */
    protected String getLayout(){
        return Configuration.getDefaultLayout();
    }


    public boolean injectable() {

        //TODO: should be optimized in non - reload mode.


        Method[] methods = getClass().getMethods();
        for (Method method : methods) {
            Annotation[] annotations = method.getAnnotations();
            for (Annotation annotation : annotations) {
                Class c = annotation.annotationType();
                if (annotation.annotationType().toString().equals(Inject.class.toString())) {
                    return true;
                }
            }
        }
        return false;
    }



/**
     * Returns HttpMethod for an action. By default if actions do not specify a method, and the controller is not
     * {@link org.javalite.activeweb.annotations.RESTful}, then an action is open for GET requests. If a controller is
     * {@link org.javalite.activeweb.annotations.RESTful}, then the actions will conform to the restful routes.
     *
     * @param actionMethodName name of action method.
     * @return {@link HttpMethod} this action will respond to.
     */
    public HttpMethod getActionHttpMethod(String actionMethodName) {
        if (restful()) {
            return getRestfulActionMethod(actionMethodName);
        } else {
            try {
                //TODO: this is using reflection twice for the same thing within one request, refactor please                
                Method method = getClass().getMethod(actionMethodName);
                Annotation[] annotations = method.getAnnotations();
                if (annotations.length > 0 && restful()) {
                    throw new InitException("Controller: " + getClass() + " is mis-configured. If a @RESTful " +
                            "annotation is used, no action annotations are allowed: @GET, @POST, @PUT, @DELETE. " +
                            "Offending action: " + actionMethodName);
                } else if (annotations.length > 1) {
                    throw new InitException("Controller: " + getClass() + " is mis-configured. Actions cannot " +
                            "specify more than one HTTP method. Only one of these annotations allowed on any action:" +
                            "@GET, @POST, @PUT, @DELETE");
                }

                //default behavior: GET method!
                if (annotations.length == 0) {
                    return HttpMethod.GET;
                } else {
                    return HttpMethod.method(annotations[0]);
                }
            }
            catch (NoSuchMethodException e) {
                throw new ActionNotFoundException(e);
            }
        }
    }

    private HttpMethod getRestfulActionMethod(String action) {
        if (action.equals("index")) {
            return HttpMethod.GET;
        } else if (action.equals("newForm")) {
            return HttpMethod.GET;
        } else if (action.equals("create")) {
            return HttpMethod.POST;
        } else if (action.equals("show")) {
            return HttpMethod.GET;
        } else if (action.equals("editForm")) {
            return HttpMethod.GET;
        } else if (action.equals("update")) {
            return HttpMethod.PUT;
        } else if (action.equals("destroy")) {
            return HttpMethod.DELETE;
        } else throw new IllegalArgumentException("Restful controllers are allowed to have the only " +
                "following actions: index, newForm, create, show, editForm, update, destroy");
    }


    /**
     * Returns true if this controller is configured to be {@link org.javalite.activeweb.annotations.RESTful}.
     * @return true if this controller is restful, false if not.
     */
    public boolean restful() {
        return getClass().getAnnotation(RESTful.class) != null;
    }

    public static <T extends AppController> boolean restful(Class<T> controllerClass){
        return controllerClass.getAnnotation(RESTful.class) != null;
    }
}