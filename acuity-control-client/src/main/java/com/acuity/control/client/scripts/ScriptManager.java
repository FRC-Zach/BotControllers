package com.acuity.control.client.scripts;

import com.acuity.common.util.Pair;
import com.acuity.control.client.BotControl;
import com.acuity.db.domain.vertex.impl.bot_clients.BotClientConfig;
import com.acuity.db.domain.vertex.impl.scripts.selector.ScriptEvaluator;
import com.acuity.db.domain.vertex.impl.scripts.selector.ScriptNode;
import com.acuity.db.domain.vertex.impl.scripts.selector.ScriptSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by Zachary Herridge on 8/21/2017.
 */
public class ScriptManager {

    private static final Logger logger = LoggerFactory.getLogger(ScriptManager.class);

    private final Object lock = new Object();

    private BotControl botControl;

    private Pair<String, Object> currentTaskPair;
    private Pair<String, Object> currentScriptPair;

    private Map<String, Object> scriptInstances = new ConcurrentHashMap<>();

    public ScriptManager(BotControl botControl) {
        this.botControl = botControl;
    }

    public void onLoop() {
        BotClientConfig botClientConfig = botControl.getBotClientConfig();
        if (botClientConfig == null) {
            logger.debug("onLoop - null botClientConfig.");
            return;
        }

        Pair<String, Object> currentTaskPair = this.currentTaskPair;
        ScriptNode currentTaskNode = currentTaskPair != null ? botClientConfig.getTask(currentTaskPair.getKey()).orElse(null) : null;
        if (currentTaskNode != null){
            if (evaluate(currentTaskPair, currentTaskNode)){
                botControl.getRsAccountManager().handle(currentTaskNode.getSettings());
            }
            return;
        }

        Pair<String, Object> currentScriptPair = this.currentScriptPair;
        ScriptNode currentScriptNode = currentScriptPair != null ? botClientConfig.getScriptSelector().getScriptNode(currentScriptPair.getKey()).orElse(null) : null;
        if (currentScriptNode == null){
           selectNextScript(null);
        }
        else if (evaluate(currentScriptPair, currentScriptNode)){
            botControl.getRsAccountManager().handle(currentScriptNode.getSettings());
        }
    }

    private void selectNextScript(ScriptNode currentScriptNode){
        BotClientConfig botClientConfig = botControl.getBotClientConfig();
        if (botClientConfig == null) {
            logger.debug("selectNextScript - null botClientConfig.");
            return;
        }

        logger.debug("Selecting next script. {}", currentScriptNode);

        ScriptSelector scriptSelector = botClientConfig.getScriptSelector();
        if (scriptSelector != null && scriptSelector.getNodeList() != null && scriptSelector.getNodeList().size() > 0){
            List<ScriptNode> nodeList = botClientConfig.getScriptSelector().getNodeList();
            int index = currentScriptNode == null ? -1 : nodeList.indexOf(currentScriptNode);
            logger.debug("Initial index. {}/{}", index, nodeList.size() - 1);
            index++;
            if (index >= nodeList.size()) index = 0;

            ScriptNode scriptNode = nodeList.get(index);
            logger.debug("Next script node. {} @ {}/{}", scriptNode, index, nodeList.size() - 1);

            List<ScriptEvaluator> startEvaluators = scriptNode.getEvaluatorGroup().getStartEvaluators();
            if (startEvaluators == null || startEvaluators.size() == 0 || ScriptConditionEvaluator.evaluate(botControl, startEvaluators)){
                this.currentScriptPair = new Pair<>(scriptNode.getUID(), getScriptInstanceOf(scriptNode));
                logger.info("Selected new current script. {}", currentScriptPair);
                botControl.sendClientState();
            }
            else {
                logger.info("Failed start evaluation.");
                selectNextScript(scriptNode);
            }
        }
    }

    private boolean evaluate(Pair<String, Object> currentScriptPair, ScriptNode currentScriptNode){
        List<ScriptEvaluator> runEvaluators = currentScriptNode.getEvaluatorGroup().getRunEvaluators();
        logger.debug("Evaluating runEvaluators - {}.", runEvaluators);
        if (runEvaluators != null && runEvaluators.size() > 0 && !ScriptConditionEvaluator.evaluate(botControl, currentScriptNode.getEvaluatorGroup().getRunEvaluators())){
            onScriptEnded(currentScriptPair);
            return false;
        }

        List<ScriptEvaluator> stopEvaluators = currentScriptNode.getEvaluatorGroup().getStopEvaluators();
        logger.debug("Evaluating stopEvaluators - {}.", stopEvaluators);
        if (stopEvaluators != null && stopEvaluators.size() > 0 && ScriptConditionEvaluator.evaluate(botControl, stopEvaluators)){
            onScriptEnded(currentScriptPair);
            return false;
        }

        return true;
    }

    private void handleAccountTransition(ScriptNode executionConfig) {
        if (executionConfig == null) {
            logger.debug("Handling Account Transition - no next script.");
            botControl.getRsAccountManager().clearRSAccount();
        }
    }

    public void onBotClientConfigUpdate(BotClientConfig botClientConfig) {
        synchronized (lock) {
            logger.debug("Received Updated Client Config - {}.", botClientConfig.hashCode());

            List<ScriptNode> taskNodeList = botClientConfig.getTaskNodeList();
            if (taskNodeList != null && taskNodeList.size() > 0){
                ScriptNode taskNode = taskNodeList.get(0);
                if (currentTaskPair == null || !Objects.equals(taskNode.getUID(), currentTaskPair.getKey())){
                    logger.info("Updated BotClientConfig contains new task at position 0. {}", taskNode);
                    this.currentTaskPair = new Pair<>(taskNode.getUID(), getScriptInstanceOf(taskNode));
                }
            }

            cleanUpScriptInstances();
        }
    }

    private void cleanUpScriptInstances() {
        synchronized (lock) {
            logger.debug("Cleaning Up Script Instances.");
        }
    }

    private void destroyInstance(Object instance) {
        try {
            botControl.destroyInstanceOfScript(instance);
        } catch (Throwable e) {
            logger.error("Error during onExit.", e);
        }
    }

    private Object getScriptInstanceOf(ScriptNode scriptNode) {
        if (scriptNode != null) {
            if (!scriptInstances.containsKey(scriptNode.getUID())) {
                logger.debug("Creating Script Instance - {}.", scriptNode.getUID());
                Object instanceOfScript = botControl.createInstanceOfScript(scriptNode);
                logger.debug("Creation of {} complete.", instanceOfScript);
                if (instanceOfScript != null) {
                    scriptInstances.put(scriptNode.getUID(), instanceOfScript);
                    return instanceOfScript;
                }

            }
            return scriptInstances.get(scriptNode.getUID());
        }
        return null;
    }

    public Optional<Pair<String, Object>> getScriptInstance() {
        if (currentTaskPair != null) return Optional.of(currentTaskPair);
        return Optional.ofNullable(currentScriptPair);
    }

    public void onScriptEnded(Pair<String, Object> closedNode) {
        synchronized (lock){
            if (closedNode == null) return;

            String collect = Arrays.stream(Thread.currentThread().getStackTrace()).map(stackTraceElement -> stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName()).collect(Collectors.joining(", "));
            logger.debug("Script pair stopped - {}. from {}", closedNode, collect);

            BotClientConfig botClientConfig = botControl.getBotClientConfig();
            ScriptNode scriptNode = botClientConfig.getScriptNode(closedNode.getKey()).orElse(null);
            if (scriptNode == null){
                logger.debug("ScriptNode not found.");
            }
            else {
                boolean task = botClientConfig.getTask(scriptNode.getUID()).isPresent();
                logger.debug("ScriptNode found. task={}", task);
                if (task){
                    boolean removed = botClientConfig.getTaskNodeList().removeIf(taskNode -> Objects.equals(taskNode.getUID(), scriptNode.getUID()));
                    boolean updated = botControl.updateClientConfig(botClientConfig, true);
                    logger.debug("ScriptNode was task. removed={}, updated={}", removed, updated);
                }
                else {
                    if ((boolean) scriptNode.getSettings().getOrDefault("completeOnStop", false)){
                        logger.debug("ScriptNode was completeOnStop.");
                        scriptNode.setComplete(true);
                        botControl.updateClientConfig(botClientConfig, true);
                    }

                    if ((boolean) scriptNode.getSettings().getOrDefault("destroyInstanceOnStop", true)){
                        logger.debug("ScriptNode was destroyInstanceOnStop.");
                        destroyInstance(closedNode.getValue());
                        scriptInstances.remove(closedNode.getKey());
                    }
                }
            }

            if (getScriptInstance().map(pair -> Objects.equals(closedNode.getKey(), pair.getKey())).orElse(false)){
                logger.debug("ScriptNode was currentScriptPair.");
                currentScriptPair = null;
                handleAccountTransition(scriptNode);
                botControl.sendClientState();
            }
        }
    }
}
