package net.arksea.pusher.server.cast;

import net.arksea.pusher.entity.PushTarget;

import javax.script.ScriptException;

/**
 *
 * Created by xiaohaixing on 2017/11/21.
 */
public interface IUserFilter {
    boolean doFilter(PushTarget pushTarget) throws ScriptException;
}
