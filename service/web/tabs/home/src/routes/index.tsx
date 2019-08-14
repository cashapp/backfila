import * as React from "react"
import {Route, Switch} from "react-router"
import {BackfillStatusContainer, ServiceContainer, TabContainer} from "../containers"

const routes = (
  <div>
    <Switch>
      <Route path="/app/home/services/:service" component={ServiceContainer} />
      <Route
        path="/app/home/backfills/:id"
        component={BackfillStatusContainer}
      />
      <Route path="/_admin/home/" component={TabContainer} />
      <Route path="/app/home/" component={TabContainer} />
      <Route path="/_tab/home/" component={TabContainer} />
      {/* Do not include a Route without a path or it will display during on all tabs */}
    </Switch>
  </div>
)

export default routes
