package org.restcomm.connect.rvd.storage;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.restcomm.connect.rvd.RvdConfiguration;
import org.restcomm.connect.rvd.exceptions.ProjectDoesNotExist;
import org.restcomm.connect.rvd.exceptions.StreamDoesNotFitInFile;
import org.restcomm.connect.rvd.logging.system.LoggingHelper;
import org.restcomm.connect.rvd.logging.system.RvdLoggers;
import org.restcomm.connect.rvd.model.CallControlInfo;
import org.restcomm.connect.rvd.model.ProjectParameters;
import org.restcomm.connect.rvd.model.ProjectSettings;
import org.restcomm.connect.rvd.model.client.WavItem;
import org.restcomm.connect.rvd.model.project.Node;
import org.restcomm.connect.rvd.model.project.ProjectState;
import org.restcomm.connect.rvd.model.server.ProjectIndex;
import org.restcomm.connect.rvd.storage.exceptions.StorageEntityNotFound;
import org.restcomm.connect.rvd.storage.exceptions.StorageException;
import org.restcomm.connect.rvd.utils.RvdUtils;
import org.restcomm.connect.rvd.utils.Zipper;
import org.restcomm.connect.rvd.utils.exceptions.ZipperException;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author otsakir@gmail.com - Orestis Tsakiridis
 */
public class FsProjectDao implements ProjectDao {

    static Logger logger = RvdLoggers.local;

    WorkspaceStorage workspaceStorage;

    public FsProjectDao(WorkspaceStorage workspaceStorage) {
//        if (RvdUtils.isEmpty(applicationName)) {
//            throw new IllegalStateException("Application name is null. Cannot create FsProjectDao");
//        }
//        this.applicationName = applicationName;
        this.workspaceStorage = workspaceStorage;
    }

    @Override
    public boolean projectExists(String applicationId) {
        return workspaceStorage.entityExists(applicationId, "state");
    }

    @Override
    public ProjectState loadProject(String applicationId) throws StorageException {
        try {
            return FsProjectStorage.loadProject(applicationId, workspaceStorage);
        } catch (StorageEntityNotFound e) {
            return null;
        }
    }

    @Override
    public ProjectIndex loadProjectOptions(String applicationId) throws StorageException {
        try {
            return FsProjectStorage.loadProjectOptions(applicationId, workspaceStorage);
        } catch (StorageEntityNotFound e) {
            return null;
        }
    }

    @Override
    public Node loadNode(String moduleName, String applicationId) throws StorageException {
        return FsProjectStorage.loadNode(applicationId,moduleName,workspaceStorage);
    }

    @Override
    public String loadBootstrapInfo(String applicationId) throws StorageException {
        try {
            return FsProjectStorage.loadBootstrapInfo(applicationId, workspaceStorage);
        } catch (StorageEntityNotFound e) {
            return null;
        }
    }

    @Override
    public ProjectSettings loadSettings(String applicationId) throws StorageException {
        try {
            return FsProjectStorage.loadProjectSettings(applicationId, workspaceStorage);
        }   catch (StorageEntityNotFound e) {
            return null;
        }
    }

    @Override
    public CallControlInfo loadWebTriggerInfo(String applicationId) throws StorageException {
        try {
            CallControlInfo webTriggerInfo = workspaceStorage.loadEntity("cc", applicationId, CallControlInfo.class);
            return webTriggerInfo;
        } catch (StorageEntityNotFound e) {
            return null;
        }
    }

    @Override
    public void storeWebTriggerInfo(CallControlInfo webTriggerInfo, String applicationId) throws StorageException {
        workspaceStorage.storeEntity(webTriggerInfo, CallControlInfo.class, "cc", applicationId);
    }

    @Override
    public void removeWebTriggerInfo(String applicationId) {
        workspaceStorage.removeEntity("cc", applicationId);
    }

    @Override
    public void storeSettings(ProjectSettings projectSettings, String applicationId) throws StorageException {
        FsProjectStorage.storeProjectSettings(projectSettings, applicationId, workspaceStorage);
    }

    @Override
    public String loadProjectStateRaw(String applicationId) throws StorageException {
        return FsProjectStorage.loadProjectString(applicationId, workspaceStorage);
    }

    @Override
    public void createProject(String applicationId, ProjectState projectState) throws StorageException {
        FsProjectStorage.createProjectSlot(applicationId, workspaceStorage);
        FsProjectStorage.storeProject(true, projectState, applicationId, workspaceStorage);
    }

    /**
     * Creates a copy of the project at location pointed to by 'sourcePath' using 'applicationId' as
     * identifier.
     *
     * Note, source path points at the project directory itself
     *  @param applicationId
     * @param sourcePath
     * @param owner
     */
    @Override
    public void createProjectFromLocation(String applicationId, String sourcePath, String owner) throws StorageException {
        // create a directory in the filesystem to host the new project
        FsProjectStorage.createProjectSlot(applicationId, workspaceStorage);
        // create state and project structure
        ProjectState projectState = workspaceStorage.loadEntity("state", sourcePath, ProjectState.class);
        if (owner != null) {
            projectState.getHeader().setOwner(owner);
        }

        FsProjectStorage.storeProject(true, projectState, applicationId, workspaceStorage);
        // copy project settings
        try {
            ProjectSettings settings = workspaceStorage.loadEntity("settings", sourcePath, ProjectSettings.class);
            storeSettings(settings, applicationId);
        } catch (StorageEntityNotFound e) {
            // do nothing if the settings are not found
        }
        // copy web-trigger information
        try {
            CallControlInfo webTriggerInfo = workspaceStorage.loadEntity("cc", sourcePath, CallControlInfo.class);
            workspaceStorage.storeEntity(webTriggerInfo, CallControlInfo.class, "cc", applicationId);
        } catch (StorageEntityNotFound e) {
            // do nothing if webTrigger info is not there
        }
        // copy .wav/media resources
        List<WavItem> wavs = listMedia(new File(sourcePath));
        for (WavItem wav: wavs) {
            String sourceWavPath = sourcePath + File.separator + RvdConfiguration.WAVS_DIRECTORY_NAME;
            addRawResource(applicationId, RvdConfiguration.WAVS_DIRECTORY_NAME, sourceWavPath, wav.getFilename());
        }
    }

    @Override
    public void createProjectFromTemplate(String applicationId, String templateId, String projectAlias, ProjectTemplateDao templateDao, String owner) throws StorageException {
        String sourceProjectPath = ((FsProjectTemplateDao)templateDao).resolveTemplateProjectPath(templateId, projectAlias);
        createProjectFromLocation(applicationId, sourceProjectPath, owner );
    }

    /**
     * Adds a resource (file) to a project
     *
     * @param applicationId
     * @param relativePath path inside the project to add the resource under. For example "wavs". No forward/trailing slashes should be inscluded. TODO Use symbolic names at some point to describe the type of the resource instead of the relative path like WAV_RESOURCE
     * @param resourcePath absolute path (parent dir) to the resource that will be copied
     * @param resourceName the filename of the resource (includes file extension like .wav)
     * @throws StorageException
     */
    void addRawResource(String applicationId, String relativePath, String resourcePath, String resourceName) throws StorageException {
        String destinationFilePath = workspaceStorage.rootPath + File.separator + applicationId + File.separator + relativePath +  File.separator + resourceName;
        String sourceFilePath = resourcePath + File.separator + resourceName;
        try {
            FileUtils.copyFile(new File(sourceFilePath), new File(destinationFilePath));
        } catch (IOException e) {
            throw new StorageException("Error copying resource " + destinationFilePath + " in project " + applicationId);
        }
    }

    @Override
    public List<WavItem> listMedia(String applicationId) throws StorageException {
        File projectDir = new File(workspaceStorage.rootPath + File.separator + applicationId);
        return listMedia(projectDir);
    }

    @Override
    public ProjectParameters loadProjectParameters(String applicationId) throws StorageException {
        try {
            ProjectParameters parameters = workspaceStorage.loadEntity("parameters", applicationId, ProjectParameters.class);
            return parameters;
        } catch (StorageEntityNotFound e) {
            return  null;
        }
    }

    @Override
    public void storeProjectParameters(String applicationId, ProjectParameters parameters) throws StorageException {
        workspaceStorage.storeEntity(parameters, ProjectParameters.class, "parameters", applicationId);
    }

    @Override
    public void removeProject(String applicationId) throws ProjectDoesNotExist, StorageException {
        FsProjectStorage.deleteProject(applicationId,workspaceStorage);
    }

    @Override
    public void updateProjectState(String applicationId, ProjectState state) throws StorageException {
        workspaceStorage.storeEntity(state, "state", applicationId);
    }


    @Override
    public InputStream archiveProject(String projectName) throws StorageException {
        String path = workspaceStorage.resolveWorkspacePath(projectName);
        File tempFile;
        try {
            tempFile = File.createTempFile("RVDprojectArchive",".zip");
        } catch (IOException e1) {
            throw new StorageException("Error creating temp file for archiving project " + projectName, e1);
        }

        InputStream archiveStream;
        try {
            Zipper zipper = new Zipper(tempFile);
            zipper.addDirectoryRecursively(path, false);
            zipper.finish();

            // open a stream on this file
            archiveStream = new FileInputStream(tempFile);
            return archiveStream;
        } catch (ZipperException e) {
            throw new StorageException( "Error archiving " + projectName, e);
        } catch (FileNotFoundException e) {
            throw new StorageException("This is weird. Can't find the temp file i just created for archiving project " + projectName, e);
        } finally {
            // Always delete the file. The underlying file content still exists because the archiveStream refers to it (for Linux only). It will be deleted when the stream is closed
            tempFile.delete();
        }
    }

    @Override
    public void storeWav(String projectName, String wavname, InputStream wavStream, Integer maxSize) throws StorageException, StreamDoesNotFitInFile {
        String wavPathname = workspaceStorage.resolveWorkspacePath(projectName + File.separator +  RvdConfiguration.WAVS_DIRECTORY_NAME + File.separator + wavname);
        if(logger.isDebugEnabled())
            logger.log(Level.DEBUG, LoggingHelper.buildMessage(FsProjectStorage.class,"storeWav", "writing wav file to {0}", wavPathname));
        try {
            RvdUtils.streamToFile(wavStream, new File(wavPathname), maxSize);
        } catch (IOException e) {
            throw new StorageException("Error writing to " + wavPathname, e);
        }
    }

    /**
     * Generates a list of media files for a project at specific path
     *
     * TODO change WavItem to MediaItem
     *
     * @param projectPath absolute path of the project
     * @return
     * @throws StorageException
     */
    List<WavItem> listMedia(File projectPath) throws StorageException {
        List<WavItem> items = new ArrayList<WavItem>();

        File wavsDir = new File(projectPath.getPath() + File.separator + RvdConfiguration.WAVS_DIRECTORY_NAME);
        if (wavsDir.exists()) {

            File[] entries = wavsDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File anyfile) {
                    if (anyfile.isFile())
                        return true;
                    return false;
                }
            });
            Arrays.sort(entries, new Comparator<File>() {
                public int compare(File f1, File f2) {
                    return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified()) ;
                }
            });

            for (File entry : entries) {
                WavItem item = new WavItem();
                item.setFilename(entry.getName());
                items.add(item);
            }
        }

        return items;
    }
}
