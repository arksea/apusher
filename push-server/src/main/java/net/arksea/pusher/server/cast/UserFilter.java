package net.arksea.pusher.server.cast;

import net.arksea.pusher.entity.PushTarget;
import groovy.json.JsonSlurper;
import org.springframework.util.StringUtils;

import javax.script.*;

/**
 *
 * Created by xiaohaixing on 2017/11/21.
 */
public class UserFilter implements IUserFilter {
    private final JsonSlurper jsonSlurper = new JsonSlurper();
    private final ScriptEngine scriptEngine;
    private final CompiledScript script;

    public UserFilter(String script) throws ScriptException {
        if (StringUtils.isEmpty(script)) {
            this.scriptEngine = null;
            this.script = null;
        } else {
            final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
            this.scriptEngine = scriptEngineManager.getEngineByName("groovy");
            Compilable compilingEngine = (Compilable)this.scriptEngine;
            this.script = compilingEngine.compile(script); //todo: 缓存已编译脚本
        }
    }

    @Override
    public boolean doFilter(PushTarget pushTarget) throws ScriptException {
        if (script == null) {
            return true;
        } else if (StringUtils.isEmpty(pushTarget.getToken()) || !pushTarget.isTokenActived()) {
            return false;
        } else {
            Bindings binding = new SimpleBindings();
            Object userInfoObj = null;
            if (!StringUtils.isEmpty(pushTarget.getUserInfo())) {
                userInfoObj = jsonSlurper.parseText(pushTarget.getUserInfo());
            }
            binding.put("info", userInfoObj);
            binding.put("target", pushTarget);
            binding.put("filter", this);
            return (boolean) this.script.eval(binding);
        }
    }

    public boolean versionBetween(String ver, String min, String max) {
        return (StringUtils.isEmpty(min) || versionCompare(ver, min) != -1)
            && (StringUtils.isEmpty(max) || versionCompare(ver, max) !=  1);
    }

    public static int versionCompare(final String str1, final String str2) {
        final String[] vals1 = str1.split("\\.");
        final String[] vals2 = str2.split("\\.");
        int i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }
        // compare first non-equal ordinal number
        if (i < vals1.length && i < vals2.length) {
            final int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
            return Integer.signum(diff);
        }
        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        return Integer.signum(vals1.length - vals2.length);
    }
}
