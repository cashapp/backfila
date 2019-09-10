import {
  createAction,
  defaultRootState,
  IAction,
  IRootState,
  SimpleReduxSaga
} from "@misk/simpleredux"
import axios from "axios"
import { Map } from "immutable"
import { all, call, put, takeLatest } from "redux-saga/effects"

/**
 * Actions
 * string enum of the defined actions that is used as type enforcement for Reducer and Sagas
 * arguments
 */
export enum HOME {
  DINOSAUR = "HOME_DINOSAUR",
  SUCCESS = "HOME_SUCCESS",
  FAILURE = "HOME_FAILURE"
}

/**
 * Dispatch Object
 * Object of functions that dispatch Actions with standard defaults and any required passed in input
 * dispatch Object is used within containers to initiate any saga provided functionality
 */
export interface IHomePayload {
  data?: any
  error: any
  loading: boolean
  success: boolean
}

export interface IDispatchHome {
  homeDinosaur: (
    data: any,
    fieldTag: string,
    formTag: string
  ) => IAction<HOME.DINOSAUR, IHomePayload>
  homeFailure: (error: any) => IAction<HOME.FAILURE, IHomePayload>
  homeSuccess: (data: any) => IAction<HOME.SUCCESS, IHomePayload>
}

export const dispatchHome: IDispatchHome = {
  homeDinosaur: () =>
    createAction<HOME.DINOSAUR, IHomePayload>(HOME.DINOSAUR, {
      error: null,
      loading: true,
      success: false
    }),
  homeFailure: (error: any) =>
    createAction<HOME.FAILURE, IHomePayload>(HOME.FAILURE, {
      ...error,
      loading: false,
      success: false
    }),
  homeSuccess: (data: any) =>
    createAction<HOME.SUCCESS, IHomePayload>(HOME.SUCCESS, {
      ...data,
      error: null,
      loading: false,
      success: true
    })
}

/**
 * Sagas are generating functions that consume actions and
 * pass either latest (takeLatest) or every (takeEvery) action
 * to a handling generating function.
 *
 * Handling function is where obtaining web resources is done
 * Web requests are done within try/catch so that
 *  if request fails: a failure action is dispatched
 *  if request succeeds: a success action with the data is dispatched
 * Further processing of the data should be minimized within the handling
 *  function to prevent unhelpful errors. Ie. a failed request error is
 *  returned but it actually was just a parsing error within the try/catch.
 */
function* handleDinosaur(action: IAction<HOME, IHomePayload>) {
  try {
    const { data } = yield call(
      axios.get,
      "https://jsonplaceholder.typicode.com/posts/"
    )
    yield put(dispatchHome.homeSuccess({ data }))
  } catch (e) {
    yield put(dispatchHome.homeFailure({ error: { ...e } }))
  }
}

export function* watchHomeSagas(): SimpleReduxSaga {
  yield all([takeLatest(HOME.DINOSAUR, handleDinosaur)])
}

/**
 * Initial State
 * Reducer merges all changes from dispatched action objects on to this initial state
 */
const initialState = defaultRootState("home")

/**
 * Duck Reducer
 * Merges dispatched action objects on to the existing (or initial) state to generate new state
 */
export const HomeReducer = (
  state = initialState,
  action: IAction<HOME, {}>
) => {
  switch (action.type) {
    case HOME.DINOSAUR:
    case HOME.FAILURE:
    case HOME.SUCCESS:
      return state.merge(action.payload)
    default:
      return state
  }
}

/**
 * State Interface
 * Provides a complete Typescript interface for the object on state that this duck manages
 * Consumed by the root reducer in ./ducks index to update global state
 * Duck state is attached at the root level of global state
 */
export interface IHomeState extends IRootState {
  [key: string]: any
}

export interface IHomeImmutableState extends Map<string, any> {
  toJS: () => IHomeState
}
