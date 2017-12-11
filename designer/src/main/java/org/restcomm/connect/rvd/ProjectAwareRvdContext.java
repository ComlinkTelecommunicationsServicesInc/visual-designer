package org.restcomm.connect.rvd;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.restcomm.connect.rvd.concurrency.ResidentProjectInfo;
import org.restcomm.connect.rvd.exceptions.ProjectDoesNotExist;
import org.restcomm.connect.rvd.logging.ProjectLogger;
import org.restcomm.connect.rvd.logging.system.LoggingContext;
import org.restcomm.connect.rvd.model.ProjectSettings;
import org.restcomm.connect.rvd.model.server.ProjectOptions;
import org.restcomm.connect.rvd.storage.ProjectDao;
import org.restcomm.connect.rvd.storage.exceptions.StorageException;

/**
 * Holds information about the targeted project. It follows the request lifecycle.
 *
 * @author otsakir@gmail.com - Orestis Tsakiridis
 *
 */
public class ProjectAwareRvdContext extends RvdContext {

    private String projectName;
    private ProjectLogger projectLogger;
    private ProjectSettings projectSettings;
    private ProjectOptions projectOptions; // project options that is loaded on-demand

    public ProjectAwareRvdContext(String projectName, ResidentProjectInfo residentInfo, HttpServletRequest request, ServletContext servletContext, RvdConfiguration configuration, LoggingContext loggingPrefix, ProjectDao projectDao) throws ProjectDoesNotExist {
        super(request, servletContext, configuration, loggingPrefix);
        if (projectName == null)
            throw new IllegalArgumentException();
        // setup application logging
        this.projectLogger = new ProjectLogger(projectName, getConfiguration(), getMarshaler(), residentInfo.logRotationSemaphore);
        // initialize project settings and options (i.e. /data/project file)
        try {
            this.projectSettings = projectDao.loadSettings(projectName);
            if (this.projectSettings == null) { // if there are no settings yet, create default settings
                this.projectSettings = ProjectSettings.createDefault();
            }
            projectOptions = projectDao.loadProjectOptions(projectName);
            if (projectOptions == null)
                throw new ProjectDoesNotExist("Project '" + projectName + "' does not exist.");
        } catch (StorageException e) {
            throw new RuntimeException(e); // serious error
        }

    }

//    public ProjectAwareRvdContext(HttpServletRequest request, ServletContext servletContext, RvdConfiguration configuration) {
//        super(request, servletContext, configuration);
//    }

    public ProjectLogger getProjectLogger() {
        return projectLogger;
    }

    public ProjectSettings getProjectSettings() {
        return projectSettings;
    }

    public ProjectOptions getProjectOptions() {
        return projectOptions;
    }
}
