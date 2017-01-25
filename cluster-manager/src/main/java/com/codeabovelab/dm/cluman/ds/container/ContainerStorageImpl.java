/*
 * Copyright 2016 Code Above Lab LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.codeabovelab.dm.cluman.ds.container;

import com.codeabovelab.dm.cluman.model.ContainerBase;
import com.codeabovelab.dm.cluman.model.ContainerBaseIface;
import com.codeabovelab.dm.cluman.model.DockerContainer;
import com.codeabovelab.dm.common.kv.mapping.KvMap;
import com.codeabovelab.dm.common.kv.mapping.KvMapperFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class ContainerStorageImpl implements ContainerStorage, InitializingBean {

    final KvMap<ContainerRegistration> map;

    @Autowired
    public ContainerStorageImpl(KvMapperFactory kvmf) {
        String prefix = kvmf.getStorage().getPrefix() + "/containers/";
        this.map = KvMap.builder(ContainerRegistration.class)
          .mapper(kvmf)
          .path(prefix)
          .factory((key, type) -> new ContainerRegistration(this, key))
          .build();
    }

    @Override
    public void afterPropertiesSet() {
        this.map.load();
    }


    void deleteContainer(String id) {
        ContainerRegistration cr = map.remove(id);
        if(cr != null) {
            log.info("Container remove: {} ", cr.forLog());
        }
    }

    @Override
    public List<ContainerRegistration> getContainers() {
        ArrayList<ContainerRegistration> list = new ArrayList<>(map.values());
        list.removeIf(cr -> cr.getContainer() == null);
        return list;
    }

    @Override
    public ContainerRegistration getContainer(String id) {
        ContainerRegistration cr = map.get(id);
        return check(cr);
    }

    private ContainerRegistration check(ContainerRegistration cr) {
        // we must not return invalid registrations
        if(cr == null) {
            return null;
        }
        DockerContainer dc = cr.getContainer();
        if(dc == null) {
            // remove invalid container
            deleteContainer(cr.getId());
            return null;
        }
        return cr;
    }

    @Override
    public ContainerRegistration findContainer(String name) {
        ContainerRegistration cr = map.get(name);
        if(cr == null) {
            cr = map.values().stream().filter((item) -> {
                DockerContainer container = item.getContainer();
                return container != null && (item.getId().startsWith(name) || container.getName().equals(name));
            }).findAny().orElse(null);
        }
        return check(cr);
    }

    @Override
    public List<ContainerRegistration> getContainersByNode(String nodeName) {
        return containersByNode(nodeName)
          .collect(Collectors.toList());
    }

    private Stream<ContainerRegistration> containersByNode(String nodeName) {
        return map.values()
          .stream()
          .filter(c -> Objects.equals(c.getNode(), nodeName));
    }

    Set<String> getContainersIdsByNode(String nodeName) {
        return containersByNode(nodeName)
          .map(ContainerRegistration::getId)
          .collect(Collectors.toSet());
    }

    /**
     * Get or create container.
     *
     * @param container
     * @param node
     * @return
     */
    @Override
    public ContainerRegistration updateAndGetContainer(ContainerBaseIface container, String node) {
        ContainerRegistration cr = map.computeIfAbsent(container.getId(), s -> new ContainerRegistration(this, s));
        cr.from(container, node);
        log.info("Update container: {}", cr.forLog());
        return cr;
    }

    void remove(Set<String> ids) {
        ids.forEach(this::deleteContainer);
    }

    void removeNodeContainers(String nodeName) {
        Set<String> nodeIds = getContainersIdsByNode(nodeName);
        remove(nodeIds);
    }
}
