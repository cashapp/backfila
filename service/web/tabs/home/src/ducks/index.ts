import {
  dispatchSimpleForm,
  dispatchSimpleNetwork,
  IDispatchSimpleForm,
  IDispatchSimpleNetwork,
  ISimpleFormImmutableState,
  ISimpleFormState,
  ISimpleNetworkImmutableState,
  ISimpleNetworkState,
  SimpleFormReducer,
  SimpleNetworkReducer,
  SimpleReduxSaga,
  simpleRootSelector,
  watchSimpleFormSagas,
  watchSimpleNetworkSagas
} from "@misk/simpleredux"
import {
  connectRouter,
  LocationChangeAction,
  RouterState
} from "connected-react-router"
import { History } from "history"
import { AnyAction, combineReducers, Reducer } from "redux"
import { all, fork } from "redux-saga/effects"
import {
  dispatchHome,
  HomeReducer,
  IDispatchHome,
  IHomeImmutableState,
  IHomeState,
  watchHomeSagas
} from "./home"

export * from "./home"

/**
 * Redux Store State
 */
export interface IState {
  home: IHomeState
  router: Reducer<RouterState, LocationChangeAction>
  simpleForm: ISimpleFormState
  simpleNetwork: ISimpleNetworkState
}

/**
 * Dispatcher
 */
export interface IDispatchProps
  extends IDispatchSimpleForm,
    IDispatchSimpleNetwork,
    IDispatchHome {}

export const rootDispatcher: IDispatchProps = {
  ...dispatchSimpleForm,
  ...dispatchSimpleNetwork,
  ...dispatchHome
}

/**
 * State Selectors
 */
export const rootSelectors = (state: IState) => ({
  home: simpleRootSelector<IState, IHomeImmutableState>("home", state),
  router: state.router,
  simpleForm: simpleRootSelector<IState, ISimpleFormImmutableState>(
    "simpleForm",
    state
  ),
  simpleNetwork: simpleRootSelector<IState, ISimpleNetworkImmutableState>(
    "simpleNetwork",
    state
  )
})

/**
 * Reducers
 */
export const rootReducer = (history: History): Reducer<any, AnyAction> =>
  combineReducers({
    home: HomeReducer,
    router: connectRouter(history),
    simpleForm: SimpleFormReducer,
    simpleNetwork: SimpleNetworkReducer
  })

/**
 * Sagas
 */
export function* rootSaga(): SimpleReduxSaga {
  yield all([
    fork(watchHomeSagas),
    fork(watchSimpleFormSagas),
    fork(watchSimpleNetworkSagas)
  ])
}

/**
 * Map Dispatch/State to Props
 */
export const mapStateToProps = (state: IState) => rootSelectors(state)

export const mapDispatchToProps: IDispatchProps = {
  ...rootDispatcher
}
