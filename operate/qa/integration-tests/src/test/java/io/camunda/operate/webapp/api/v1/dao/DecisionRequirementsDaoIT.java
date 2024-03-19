/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.schema.indices.DecisionRequirementsIndex.DECISION_REQUIREMENTS_ID;
import static io.camunda.operate.schema.indices.DecisionRequirementsIndex.RESOURCE_NAME;
import static io.camunda.operate.schema.indices.DecisionRequirementsIndex.VERSION;
import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.operate.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.zeebeimport.util.XMLUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class DecisionRequirementsDaoIT extends OperateSearchAbstractIT {

  @Autowired private DecisionRequirementsDao dao;

  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    final String indexName = decisionRequirementsIndex.getFullQualifiedName();

    String resourceXml =
        testResourceManager.readResourceFileContentsAsString("invoiceBusinessDecisions_v_1.dmn");
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new DecisionRequirementsEntity()
            .setId("2251799813685249")
            .setKey(2251799813685249L)
            .setDecisionRequirementsId("invoiceBusinessDecisions")
            .setName("Invoice Business Decisions")
            .setVersion(1)
            .setResourceName("invoiceBusinessDecisions_v_1.dmn")
            .setTenantId(DEFAULT_TENANT_ID)
            .setXml(resourceXml));

    resourceXml =
        testResourceManager.readResourceFileContentsAsString("invoiceBusinessDecisions_v_2.dmn");
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new DecisionRequirementsEntity()
            .setId("2251799813685253")
            .setKey(2251799813685253L)
            .setDecisionRequirementsId("invoiceBusinessDecisions")
            .setName("Invoice Business Decisions")
            .setVersion(2)
            .setResourceName("invoiceBusinessDecisions_v_2.dmn")
            .setTenantId(DEFAULT_TENANT_ID)
            .setXml(resourceXml));

    searchContainerManager.refreshIndices("*operate-decision*");
  }

  @Test
  public void shouldReturnDecisionRequirements() {
    final Results<DecisionRequirements> decisionRequirementsResults = dao.search(new Query<>());

    assertThat(decisionRequirementsResults.getTotal()).isEqualTo(2);
    assertThat(decisionRequirementsResults.getItems())
        .extracting(DECISION_REQUIREMENTS_ID)
        .containsExactlyInAnyOrder("invoiceBusinessDecisions", "invoiceBusinessDecisions");
    assertThat(decisionRequirementsResults.getItems())
        .extracting(VERSION)
        .containsExactlyInAnyOrder(1, 2);
    assertThat(decisionRequirementsResults.getItems())
        .extracting(RESOURCE_NAME)
        .containsExactlyInAnyOrder(
            "invoiceBusinessDecisions_v_1.dmn", "invoiceBusinessDecisions_v_2.dmn");
  }

  @Test
  public void shouldSortDecisionRequirementsDesc() {
    final Results<DecisionRequirements> decisionRequirementsResults =
        dao.search(
            new Query<DecisionRequirements>()
                .setSort(Query.Sort.listOf(RESOURCE_NAME, Query.Sort.Order.DESC)));

    assertThat(decisionRequirementsResults.getTotal()).isEqualTo(2);
    assertThat(decisionRequirementsResults.getItems())
        .extracting(RESOURCE_NAME)
        .containsExactly("invoiceBusinessDecisions_v_2.dmn", "invoiceBusinessDecisions_v_1.dmn");
  }

  @Test
  public void shouldSortDecisionRequirementsAsc() {
    final Results<DecisionRequirements> decisionRequirementsResults =
        dao.search(
            new Query<DecisionRequirements>()
                .setSort(Query.Sort.listOf(RESOURCE_NAME, Query.Sort.Order.ASC)));

    assertThat(decisionRequirementsResults.getTotal()).isEqualTo(2);
    assertThat(decisionRequirementsResults.getItems())
        .extracting(RESOURCE_NAME)
        .containsExactly("invoiceBusinessDecisions_v_1.dmn", "invoiceBusinessDecisions_v_2.dmn");
  }

  @Test
  public void shouldPageDecisionRequirements() {
    Results<DecisionRequirements> decisionRequirementsResults =
        dao.search(
            new Query<DecisionRequirements>()
                .setSort(Query.Sort.listOf(RESOURCE_NAME, Query.Sort.Order.DESC))
                .setSize(1));

    assertThat(decisionRequirementsResults.getTotal()).isEqualTo(2);
    assertThat(decisionRequirementsResults.getItems()).hasSize(1);
    assertThat(decisionRequirementsResults.getItems().get(0).getResourceName())
        .isEqualTo("invoiceBusinessDecisions_v_2.dmn");

    final Object[] searchAfter = decisionRequirementsResults.getSortValues();

    decisionRequirementsResults =
        dao.search(
            new Query<DecisionRequirements>()
                .setSort(Query.Sort.listOf(RESOURCE_NAME, Query.Sort.Order.DESC))
                .setSize(1)
                .setSearchAfter(searchAfter));

    assertThat(decisionRequirementsResults.getTotal()).isEqualTo(2);
    assertThat(decisionRequirementsResults.getItems()).hasSize(1);
    assertThat(decisionRequirementsResults.getItems().get(0).getResourceName())
        .isEqualTo("invoiceBusinessDecisions_v_1.dmn");
  }

  @Test
  public void shouldFilterDecisionRequirements() {
    final Results<DecisionRequirements> decisionRequirementsResults =
        dao.search(
            new Query<DecisionRequirements>()
                .setFilter(
                    new DecisionRequirements()
                        .setResourceName("invoiceBusinessDecisions_v_1.dmn")));

    assertThat(decisionRequirementsResults.getTotal()).isEqualTo(1);

    assertThat(decisionRequirementsResults.getItems().get(0).getResourceName())
        .isEqualTo("invoiceBusinessDecisions_v_1.dmn");
  }

  @Test
  public void shouldReturnWhenByKey() {
    final DecisionRequirements decisionRequirements = dao.byKey(2251799813685249L);

    assertThat(decisionRequirements.getResourceName())
        .isEqualTo("invoiceBusinessDecisions_v_1.dmn");
  }

  @Test
  public void shouldThrowKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byKey(1L));
  }

  @Test
  public void shouldReturnEmptyListWhenByKeysEmpty() {
    final List<DecisionRequirements> decisionRequirementsList = dao.byKeys(Set.of());

    assertThat(decisionRequirementsList).isEmpty();
  }

  @Test
  public void shouldReturnEmptyListWhenByKeysNotExist() {
    final List<DecisionRequirements> decisionRequirementsList = dao.byKeys(Set.of(-10L, -20L));

    assertThat(decisionRequirementsList).isEmpty();
  }

  @Test
  public void shouldReturnEmptyListWhenByKeysNullKey() {
    final List<DecisionRequirements> decisionRequirementsList =
        dao.byKeys(Collections.singleton(null));

    assertThat(decisionRequirementsList).isEmpty();
  }

  @Test
  public void shouldReturnEmptyListWhenByKeysNotExistAndNullKey() {
    final List<DecisionRequirements> decisionRequirementsList =
        dao.byKeys(new HashSet<>(Arrays.asList(-10L, null)));

    assertThat(decisionRequirementsList).isEmpty();
  }

  @Test
  public void shouldReturnWhenByKeys() {
    final Set<Long> keys = Set.of(2251799813685249L, 2251799813685253L);

    final List<DecisionRequirements> decisionRequirementsList = dao.byKeys(keys);

    assertThat(decisionRequirementsList).hasSize(2);
    assertThat(decisionRequirementsList)
        .extracting(DecisionRequirementsIndex.KEY)
        .containsExactlyInAnyOrder(keys.toArray());
    assertThat(decisionRequirementsList)
        .extracting(DecisionRequirementsIndex.VERSION)
        .containsExactlyInAnyOrder(1, 2);
  }

  @Test
  public void shouldReturnWhenXmlByKey() {
    final String decisionRequirementsXml = dao.xmlByKey(2251799813685249L);

    assertThat(decisionRequirementsXml).contains("id=\"invoiceBusinessDecisions\"");
    try {
      final InputStream xmlInputStream =
          new ByteArrayInputStream(decisionRequirementsXml.getBytes(StandardCharsets.UTF_8));
      new XMLUtil()
          .getSAXParserFactory()
          .newSAXParser()
          .parse(xmlInputStream, new DefaultHandler());
    } catch (SAXException | IOException | ParserConfigurationException e) {
      fail(String.format("String '%s' should be of type xml", decisionRequirementsXml), e);
    }
  }

  @Test
  public void shouldThrowXmlKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.xmlByKey(1L));
  }
}
