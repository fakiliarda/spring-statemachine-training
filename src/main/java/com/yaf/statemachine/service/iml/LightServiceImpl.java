package com.yaf.statemachine.service.iml;

import com.yaf.statemachine.domain.LightGroupEvent;
import com.yaf.statemachine.domain.LightGroupState;
import com.yaf.statemachine.statemachine.LivingRoomStateMachine;
import com.yaf.statemachine.repository.LivingRoomStateMachineRepository;
import com.yaf.statemachine.service.LightActionStateChangeInterceptor;
import com.yaf.statemachine.service.LightService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@RequiredArgsConstructor
@Service
public class LightServiceImpl implements LightService {

    public static final String LIGHTGROUP_ID_HEADER = "lightgroup_id";
    private final LightActionStateChangeInterceptor lightActionStateChangeListener;

    private final LivingRoomStateMachineRepository livingRoomStateMachineRepository;
    private final StateMachineFactory<LightGroupState, LightGroupEvent> stateMachineFactory;

    @Override
    public LivingRoomStateMachine createLivingRoomStateMachine(LivingRoomStateMachine livingRoomStateMachine) {
        livingRoomStateMachine.setState(LightGroupState.INITIAL);
        return livingRoomStateMachineRepository.save(livingRoomStateMachine);
    }

    @Transactional
    @Override
    public StateMachine<LightGroupState, LightGroupEvent> execute(Long livingRoomStateMachineId, LightGroupEvent event) {
        var sm = build(livingRoomStateMachineId);
        sendEvent(livingRoomStateMachineId, sm, event);
        return sm;
    }

    private void sendEvent(Long livingRoomStateMachineId, StateMachine<LightGroupState, LightGroupEvent> sm, LightGroupEvent event) {

        var msg = MessageBuilder.withPayload(event)
                .setHeader(LIGHTGROUP_ID_HEADER, livingRoomStateMachineId)
                .build();

        sm.sendEvent(msg);

    }

    private StateMachine<LightGroupState, LightGroupEvent> build(Long livingRoomStateMachineId) {

        var lightGroup = livingRoomStateMachineRepository.getOne(livingRoomStateMachineId);
        var sm = stateMachineFactory.getStateMachine(Long.toString(livingRoomStateMachineId));

        sm.getStateMachineAccessor()
                .doWithAllRegions(sma -> {
                    sma.addStateMachineInterceptor(lightActionStateChangeListener);
                    sma.resetStateMachine(new DefaultStateMachineContext<>(lightGroup.getState(), null, null, null));
                });
        sm.start();
        return sm;
    }
}
