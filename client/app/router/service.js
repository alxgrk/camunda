import {$window, $document, dispatchAction, includes} from 'view-utils';
import {createRouteAction} from './reducer';

let router;

export function getRouter() {
  if (!router) {
    router = createNewRouter();
  }

  return router;
}

function createNewRouter() {
  let routes = [];
  const history = $window.history;
  const router = {
    addRoutes,
    goTo,
    getUrl,
    getRouteReducer,
    onUrlChange
  };

  $window.onpopstate = onUrlChange;

  return router;

  function onUrlChange() {
    const path = $document.location.pathname;
    const query = $document.location.search;

    for (let i = 0; i < routes.length; i++) {
      const {name, test} = routes[i];
      const params = test(path, query);

      if (params) {
        dispatchAction(createRouteAction(name, params));

        break;
      }
    }
  }

  function addRoutes(...newRoutes) {
    routes = routes.concat(
      newRoutes.map(createRoute)
    );

    return router;
  }

  function goTo(routeName, params, replace) {
    const url = getUrl(routeName, params);

    if (!url) {
      return;
    }

    if (replace) {
      history.replaceState(null, null, url);
    } else {
      history.pushState(null, null, url);
    }

    dispatchAction(createRouteAction(routeName, params));
  }

  function getUrl(routeName, params) {
    const route = findRoute(routeName);

    if (route && typeof route.construct === 'function') {
      return route.construct(params);
    }
  }

  function getRouteReducer(name) {
    const route = findRoute(name);

    return route && route.reducer;
  }

  function findRoute(name) {
    return routes.find(({name: routeName}) => {
      return routeName === name;
    });
  }
}

function createRoute({name, url, test, construct, reducer, defaults = {}}) {
  return {
    name,
    url,
    reducer,
    test: typeof test === 'function' ? test : createUrlTestForRoute(url, defaults),
    construct: typeof construct === 'function' ? construct : createUrlConstructForRoute(url, defaults)
  };
}

export function createUrlTestForRoute(patternUrl, defaults) {
  const splitingRegExp = /[\/]/;
  const patternParts = patternUrl.split(splitingRegExp);

  return (url, query) => {
    const urlParts = url.split(splitingRegExp);

    // There shouldn't be more url parts than pattern parts, so it is
    // quick way to skip obviously wrong patterns without much computation
    if (urlParts.length > patternParts.length) {
      return;
    }

    const pathParams =  patternParts.reduce((params, patternPart, index) => {
      if (!params) {
        return null;
      }

      const urlPart = urlParts[index];

      if (patternPart[0] === ':') {
        const name = patternPart.substr(1);

        params[name] = urlPart || defaults[name];
      } else if (patternPart !== urlPart) {
        return null;
      }

      return params;
    }, {});

    if (!pathParams) {
      return;
    }

    if (query === '') {
      return {
        ...defaults,
        ...pathParams
      };
    }

    const queryParams = query
      .slice(1)
      .split('&')
      .reduce((params, part) => {
        const [name, value] = part.split('=');

        return {
          ...params,
          [name]: value
        };
      }, {});

    return {
      ...defaults,
      ...queryParams,
      ...pathParams
    };
  };
}

export function createUrlConstructForRoute(patternUrl, defaults) {
  const constantParts = patternUrl
    .split(/:\w+/)
    .filter(part => part.length > 0);
  const variableParts = patternUrl
    .match(/:\w+/g);

  return (params = {}) => {
    const {usedNames, path} = getPath(params);

    const search = Object
      .keys(params)
      .filter(name => !includes(usedNames, name))
      .reduce((search, name) => {
        const value = params[name];

        return `${search === '' ? '' : search + '&'}${name}=${value}`;
      }, '');

    if (search === '') {
      return path;
    }

    return path + '?' + search;
  };

  function getPath(params) {
    const usedNames = [];

    if (!variableParts) {
      return {
        usedNames,
        path: patternUrl
      };
    }

    const path = constantParts.reduce((url, constantPart, index) => {
      const name = variableParts[index] ? variableParts[index].substr(1) : null;

      usedNames.push(name);

      return url + constantPart + (name ? params[name] || defaults[name] : '');
    }, '');

    return {
      usedNames,
      path
    };
  }
}
