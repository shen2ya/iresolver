package com.koval.jresolver.classifier.core.impl;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.koval.jresolver.classifier.configuration.ClassifierProperties;
import com.koval.jresolver.classifier.core.Classifier;
import com.koval.jresolver.classifier.core.Vectorizer;
import com.koval.jresolver.classifier.results.ClassifierResult;
import com.koval.jresolver.connector.jira.JiraConnector;
import com.koval.jresolver.connector.jira.bean.JiraIssue;
import com.koval.jresolver.connector.jira.client.JiraClient;
import com.koval.jresolver.connector.jira.client.impl.BasicJiraClient;
import com.koval.jresolver.connector.jira.configuration.ConnectorProperties;

public class DocClassifier implements Classifier {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocClassifier.class);
  private static final String DATASET_FILE_NAME = "DataSet.txt";
  private static final String VECTOR_MODEL_FILE_NAME = "VectorModel.zip";
  private static final int NUMBER_OF_NEAREST_LABELS = 10;

  private DocVectorizer docVectorizer;
  private JiraConnector jiraConnector;
  private JiraClient jiraClient;
  private String workFolder;

  public DocClassifier(ClassifierProperties classifierProperties) throws URISyntaxException, IOException {
    ConnectorProperties connectorProperties = new ConnectorProperties("connector.properties");
    init(connectorProperties, classifierProperties);
  }

  public DocClassifier(ClassifierProperties classifierProperties, String password) throws URISyntaxException, IOException {
    ConnectorProperties connectorProperties = new ConnectorProperties("connector.properties");
    connectorProperties.setPassword(password);
    init(connectorProperties, classifierProperties);
  }

  private void init(ConnectorProperties connectorProperties, ClassifierProperties classifierProperties) throws URISyntaxException {
    this.jiraConnector = new JiraConnector(connectorProperties);
    this.jiraClient = new BasicJiraClient(connectorProperties.getUrl());
    this.docVectorizer = new DocVectorizer(classifierProperties);
    this.workFolder = classifierProperties.getWorkFolder();
  }

  @Override
  public void prepare() throws IOException {
    if (DocClassifier.class.getClassLoader().getResource(DATASET_FILE_NAME) != null) {
      LOGGER.info("Skip classifier preparation. File 'DataSet.txt' is already exists.");
      return;
    }
    jiraConnector.createResolvedDataSet();
  }

  @Override
  public void configure() throws IOException {
    if (DocClassifier.class.getClassLoader().getResource(VECTOR_MODEL_FILE_NAME) != null) {
      LOGGER.info("Skip classifier configuration. File 'VectorModel.zip' is already exists.");
      return;
    }
    docVectorizer.createFromDataset(DATASET_FILE_NAME);
    docVectorizer.save(workFolder + VECTOR_MODEL_FILE_NAME);
  }

  @Override
  public ClassifierResult execute(JiraIssue actualIssue) throws URISyntaxException {
    docVectorizer.load(workFolder + VECTOR_MODEL_FILE_NAME);
    Collection<String> keys = docVectorizer.getNearestLabels(actualIssue.getDescription(), NUMBER_OF_NEAREST_LABELS);
    List<String> labels = new ArrayList<>();
    List<String> users = new ArrayList<>();
    List<String> attachments = new ArrayList<>();

    LOGGER.info("Nearest issues: " + keys);
    keys.forEach((key) -> {
      JiraIssue issue = jiraClient.getIssueByKey(key.trim());
      labels.addAll(issue.getLabels());
      if (issue.getAssignee() != null) {
        users.add(issue.getAssignee().getName());
      }
      if (issue.getReporter() != null) {
        users.add(issue.getReporter().getName());
      }
      issue.getComments().forEach((comment) -> {
        if (comment.getAuthor() != null) {
          users.add(comment.getAuthor().getName());
        }
      });
      issue.getAttachments().forEach((attachment) -> attachments.add(attachment.getFilename()));
    });
    ClassifierResult result = new ClassifierResult();
    result.setIssues(keys);
    result.setLabels(labels);
    result.setUsers(users);
    result.setAttachments(attachments);
    return result;
  }

  @Override
  public Vectorizer getVectorizer() {
    return docVectorizer;
  }

}
