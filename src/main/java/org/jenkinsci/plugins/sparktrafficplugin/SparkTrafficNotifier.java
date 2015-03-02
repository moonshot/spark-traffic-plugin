package org.jenkinsci.plugins.spark_traffic;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BallColor;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import javax.servlet.ServletException;

import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Form;

public class SparkTrafficNotifier extends Notifier {

    private final String deviceId;

    @DataBoundConstructor
    public SparkTrafficNotifier(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    private int sendSparkRestApi(
        BallColor ballColor, String accessToken, String deviceId) {

        try {
            int code = Request.Post("https://api.spark.io/v1/devices/" + deviceId + "/notify")
                .bodyForm(Form.form()
                    .add("args", ballColor.getDescription())
                    .build())
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Accept", "*/*")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .execute().returnResponse().getStatusLine().getStatusCode();
                return code;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        BallColor ballColor = build.getResult().color;
        String accessToken = getDescriptor().getAccessToken();
        int code = sendSparkRestApi(ballColor, accessToken, deviceId);
        listener.getLogger().println("Spark traffic light return code: " + code);
        return (code == 200);
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String accessToken;

        public DescriptorImpl() {
            load();
        }

        public FormValidation doTestSparkConnection(
                @QueryParameter("spark_traffic.accessToken") final String token)
                throws IOException, ServletException {

            int code = 0;

            try {
                code = Request.Get("https://api.spark.io/v1/devices/")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Accept", "*/*")
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .execute().returnResponse().getStatusLine().getStatusCode();
            } catch (Exception e) {
                e.printStackTrace();
                return FormValidation.error(
                    "Error occured: " + e.getMessage() + " (Token: " + accessToken + ")");
            }
            if (code != 200)
                return FormValidation.warning(
                    "Return code: " + code + " (Token: " + accessToken + ")");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Send build status to a spark.io powered traffic light";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            this.accessToken = formData.getString("accessToken");
            save();
            return super.configure(req, formData);
        }

        public String getAccessToken() {
            return this.accessToken;
        }
    }
}
