/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.stencil;

import static org.lwjgl.opengl.EXTFramebufferObject.*;
import static org.lwjgl.opengl.EXTFramebufferObject.GL_RENDERBUFFER_EXT;
import static org.lwjgl.opengl.EXTPackedDepthStencil.GL_DEPTH_STENCIL_EXT;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;

import lombok.experimental.UtilityClass;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;

import static org.lwjgl.opengl.GL11.GL_STENCIL_TEST;

@UtilityClass
public class Stencil implements QuickImports {

    public void push() {
        var currentWindow = mc.getWindow();
        // До любых GL-побочек: раньше delete+create рендербуфера шёл до проверки,
        // и окно null оставляло framebuffer с висячим (уже удалённым) id.
        if (currentWindow == null) {
            return;
        }
        var framebuffer = mc.getFramebuffer();
        if (framebuffer.depthAttachment > -1) {
            mc.getFramebuffer().beginWrite(false);

            glDeleteRenderbuffersEXT(framebuffer.depthAttachment);
            final int stencilDepthBufferID = glGenRenderbuffersEXT();
            glBindRenderbufferEXT(GL_RENDERBUFFER_EXT, stencilDepthBufferID);
            glRenderbufferStorageEXT(GL_RENDERBUFFER_EXT, GL_DEPTH_STENCIL_EXT, currentWindow.getWidth(), currentWindow.getHeight());
            glFramebufferRenderbufferEXT(GL_FRAMEBUFFER_EXT, GL_STENCIL_ATTACHMENT_EXT, GL_RENDERBUFFER_EXT, stencilDepthBufferID);
            glFramebufferRenderbufferEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL_RENDERBUFFER_EXT, stencilDepthBufferID);
            framebuffer.depthAttachment = -1;
        }

        glStencilMask(0xFF);
        glClear(GL_STENCIL_BUFFER_BIT);
        glEnable(GL_STENCIL_TEST);
        glStencilFunc(GL_ALWAYS, 1, 1);
        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE);
        glDisable(GL_DEPTH_TEST);
        glColorMask(false, false, false, false);
    }

    public void read(int ref) {
        glColorMask(true, true, true, true);
        glStencilFunc(GL_EQUAL, ref, 1);
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    }



    public void pop() {
        glDisable(GL_STENCIL_TEST);
        glEnable(GL_DEPTH_TEST);
        // push() выключил запись цвета (glColorMask false×4); pop без
        // промежуточного read() оставлял бы маску выключенной — весь
        // дальнейший рендер кадра стал бы невидимым.
        glColorMask(true, true, true, true);
    }
}
